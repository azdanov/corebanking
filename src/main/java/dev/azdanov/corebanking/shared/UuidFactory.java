package dev.azdanov.corebanking.shared;

import com.fasterxml.uuid.Generators;

import java.util.UUID;

public final class UuidFactory {
    private UuidFactory() {
    }

    public static UUID generate() {
        return Generators.timeBasedEpochRandomGenerator().generate();
    }
}
