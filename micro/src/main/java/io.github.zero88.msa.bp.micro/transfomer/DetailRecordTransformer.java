package io.github.zero88.msa.bp.micro.transfomer;

import io.vertx.servicediscovery.Record;

import io.github.zero88.msa.bp.micro.type.EventMessageService;

import lombok.NonNull;

class DetailRecordTransformer implements RecordTransformer {

    @Override
    public @NonNull RecordOutput transform(@NonNull Record record) {
        if (EventMessageService.TYPE.equals(record.getType())) {
            return new EventServiceTechnicalRecordTransformer().transform(record);
        }
        return RecordOutput.builder()
                           .registration(record.getRegistration())
                           .name(record.getName())
                           .type(record.getType())
                           .status(record.getStatus())
                           .location(record.getLocation().getString(Record.ENDPOINT))
                           .metadata(record.getMetadata())
                           .build();
    }

}
