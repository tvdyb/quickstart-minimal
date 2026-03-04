package com.digitalasset.quickstart.umbra;

import com.daml.ledger.api.v2.ValueOuterClass;
import com.daml.ledger.api.v2.ValueOuterClass.Value;
import com.daml.ledger.api.v2.ValueOuterClass.Record;
import com.daml.ledger.api.v2.ValueOuterClass.RecordField;

import java.util.Map;

/**
 * Helper for building Daml proto Value objects from plain Java types.
 */
public class ProtoHelper {

    public static Value textVal(String s) {
        return Value.newBuilder().setText(s).build();
    }

    public static Value partyVal(String p) {
        return Value.newBuilder().setParty(p).build();
    }

    public static Value numericVal(String n) {
        return Value.newBuilder().setNumeric(n).build();
    }

    public static Value numericVal(double d) {
        return numericVal(String.valueOf(d));
    }

    public static Value boolVal(boolean b) {
        return Value.newBuilder().setBool(b).build();
    }

    public static Value contractIdVal(String cid) {
        return Value.newBuilder().setContractId(cid).build();
    }

    public static Value enumVal(String constructor) {
        return Value.newBuilder()
                .setEnum(ValueOuterClass.Enum.newBuilder().setConstructor(constructor).build())
                .build();
    }

    public static Value variantVal(String constructor, Value value) {
        return Value.newBuilder()
                .setVariant(ValueOuterClass.Variant.newBuilder()
                        .setConstructor(constructor)
                        .setValue(value)
                        .build())
                .build();
    }

    public static Value recordVal(RecordField... fields) {
        Record.Builder rb = Record.newBuilder();
        for (RecordField f : fields) {
            rb.addFields(f);
        }
        return Value.newBuilder().setRecord(rb.build()).build();
    }

    public static RecordField field(String label, Value value) {
        return RecordField.newBuilder().setLabel(label).setValue(value).build();
    }

    public static Value unitVal() {
        return Value.newBuilder().setRecord(Record.newBuilder().build()).build();
    }

    public static Record record(RecordField... fields) {
        Record.Builder rb = Record.newBuilder();
        for (RecordField f : fields) {
            rb.addFields(f);
        }
        return rb.build();
    }
}
