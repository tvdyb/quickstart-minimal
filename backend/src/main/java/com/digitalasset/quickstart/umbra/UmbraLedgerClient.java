package com.digitalasset.quickstart.umbra;

import com.daml.ledger.api.v2.*;
import com.digitalasset.quickstart.config.LedgerConfig;
import com.digitalasset.quickstart.security.TokenProvider;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Low-level ledger client for Umbra contracts.
 * Uses raw proto commands to avoid dependency on generated DAML bindings.
 */
@Component
public class UmbraLedgerClient {

    private static final Logger logger = LoggerFactory.getLogger(UmbraLedgerClient.class);

    private final CommandServiceGrpc.CommandServiceFutureStub commands;
    private final CommandSubmissionServiceGrpc.CommandSubmissionServiceFutureStub submission;
    private final UmbraConfig umbraConfig;
    private final String appId;

    @Autowired
    public UmbraLedgerClient(LedgerConfig ledgerConfig, Optional<TokenProvider> tokenProvider, UmbraConfig umbraConfig) {
        this.umbraConfig = umbraConfig;
        this.appId = ledgerConfig.getApplicationId();

        ManagedChannelBuilder<?> builder = ManagedChannelBuilder
                .forAddress(ledgerConfig.getHost(), ledgerConfig.getPort())
                .usePlaintext();
        if (tokenProvider.isPresent()) {
            builder.intercept(new AuthInterceptor(tokenProvider.get()));
        }
        ManagedChannel channel = builder.build();
        commands = CommandServiceGrpc.newFutureStub(channel);
        submission = CommandSubmissionServiceGrpc.newFutureStub(channel);
        logger.info("UmbraLedgerClient initialized");
    }

    /**
     * Build a Daml Identifier for an Umbra template.
     */
    public ValueOuterClass.Identifier templateId(String moduleName, String entityName) {
        return ValueOuterClass.Identifier.newBuilder()
                .setPackageId(umbraConfig.getPackageId())
                .setModuleName(moduleName)
                .setEntityName(entityName)
                .build();
    }

    /**
     * Exercise a choice on a contract and wait for the transaction result.
     */
    public CompletableFuture<TransactionOuterClass.Transaction> exerciseChoice(
            String contractId,
            String moduleName,
            String entityName,
            String choiceName,
            ValueOuterClass.Value choiceArg,
            String actAs
    ) {
        String commandId = "umbra-" + UUID.randomUUID();
        CommandsOuterClass.Command cmd = CommandsOuterClass.Command.newBuilder()
                .setExercise(CommandsOuterClass.ExerciseCommand.newBuilder()
                        .setTemplateId(templateId(moduleName, entityName))
                        .setContractId(contractId)
                        .setChoice(choiceName)
                        .setChoiceArgument(choiceArg)
                        .build())
                .build();

        CommandsOuterClass.Commands cmds = CommandsOuterClass.Commands.newBuilder()
                .setCommandId(commandId)
                .addActAs(actAs)
                .addReadAs(actAs)
                .addCommands(cmd)
                .build();

        var eventFormat = TransactionFilterOuterClass.EventFormat.newBuilder()
                .putFiltersByParty(actAs, TransactionFilterOuterClass.Filters.newBuilder().build())
                .build();
        var txFormat = TransactionFilterOuterClass.TransactionFormat.newBuilder()
                .setEventFormat(eventFormat)
                .setTransactionShape(TransactionFilterOuterClass.TransactionShape.TRANSACTION_SHAPE_LEDGER_EFFECTS)
                .build();

        var request = CommandServiceOuterClass.SubmitAndWaitForTransactionRequest.newBuilder()
                .setCommands(cmds)
                .setTransactionFormat(txFormat)
                .build();

        return toCompletableFuture(commands.submitAndWaitForTransaction(request))
                .thenApply(CommandServiceOuterClass.SubmitAndWaitForTransactionResponse::getTransaction);
    }

    /**
     * Create a contract.
     */
    public CompletableFuture<Void> createContract(
            String moduleName,
            String entityName,
            ValueOuterClass.Record payload,
            String actAs
    ) {
        String commandId = "umbra-create-" + UUID.randomUUID();
        CommandsOuterClass.Command cmd = CommandsOuterClass.Command.newBuilder()
                .setCreate(CommandsOuterClass.CreateCommand.newBuilder()
                        .setTemplateId(templateId(moduleName, entityName))
                        .setCreateArguments(payload)
                        .build())
                .build();

        CommandsOuterClass.Commands cmds = CommandsOuterClass.Commands.newBuilder()
                .setCommandId(commandId)
                .addActAs(actAs)
                .addReadAs(actAs)
                .addCommands(cmd)
                .build();

        var request = CommandSubmissionServiceOuterClass.SubmitRequest.newBuilder()
                .setCommands(cmds)
                .build();

        return toCompletableFuture(submission.submit(request)).thenApply(r -> null);
    }

    /**
     * Exercise a choice with multiple actAs parties.
     */
    public CompletableFuture<TransactionOuterClass.Transaction> exerciseChoiceMulti(
            String contractId,
            String moduleName,
            String entityName,
            String choiceName,
            ValueOuterClass.Value choiceArg,
            List<String> actAs
    ) {
        String commandId = "umbra-" + UUID.randomUUID();
        CommandsOuterClass.Command cmd = CommandsOuterClass.Command.newBuilder()
                .setExercise(CommandsOuterClass.ExerciseCommand.newBuilder()
                        .setTemplateId(templateId(moduleName, entityName))
                        .setContractId(contractId)
                        .setChoice(choiceName)
                        .setChoiceArgument(choiceArg)
                        .build())
                .build();

        CommandsOuterClass.Commands.Builder cmdsBuilder = CommandsOuterClass.Commands.newBuilder()
                .setCommandId(commandId)
                .addAllActAs(actAs)
                .addAllReadAs(actAs)
                .addCommands(cmd);

        var eventFormat = TransactionFilterOuterClass.EventFormat.newBuilder();
        for (String party : actAs) {
            eventFormat.putFiltersByParty(party, TransactionFilterOuterClass.Filters.newBuilder().build());
        }
        var txFormat = TransactionFilterOuterClass.TransactionFormat.newBuilder()
                .setEventFormat(eventFormat.build())
                .setTransactionShape(TransactionFilterOuterClass.TransactionShape.TRANSACTION_SHAPE_LEDGER_EFFECTS)
                .build();

        var request = CommandServiceOuterClass.SubmitAndWaitForTransactionRequest.newBuilder()
                .setCommands(cmdsBuilder.build())
                .setTransactionFormat(txFormat)
                .build();

        return toCompletableFuture(commands.submitAndWaitForTransaction(request))
                .thenApply(CommandServiceOuterClass.SubmitAndWaitForTransactionResponse::getTransaction);
    }

    private static <T> CompletableFuture<T> toCompletableFuture(ListenableFuture<T> lf) {
        CompletableFuture<T> cf = new CompletableFuture<>();
        Futures.addCallback(lf, new FutureCallback<>() {
            @Override public void onSuccess(T result) { cf.complete(result); }
            @Override public void onFailure(@Nonnull Throwable t) { cf.completeExceptionally(t); }
        }, MoreExecutors.directExecutor());
        return cf;
    }

    private static class AuthInterceptor implements ClientInterceptor {
        private static final Metadata.Key<String> AUTH_HEADER = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
        private final TokenProvider tokenProvider;
        AuthInterceptor(TokenProvider tp) { this.tokenProvider = tp; }
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> m, CallOptions co, Channel ch) {
            return new ForwardingClientCall.SimpleForwardingClientCall<>(ch.newCall(m, co)) {
                @Override public void start(Listener<RespT> l, Metadata h) {
                    h.put(AUTH_HEADER, "Bearer " + tokenProvider.getToken());
                    super.start(l, h);
                }
            };
        }
    }
}
