package pt.flawless.fpermissions.model;

import java.util.List;

/** Definição crua de um grupo, como vem da config. */
public record GroupDef(List<String> inherits, List<String> permissions) {
}
