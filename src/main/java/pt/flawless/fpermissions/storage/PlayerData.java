package pt.flawless.fpermissions.storage;

import java.util.Set;

/** Grupos e permissões próprias (já normalizadas; negações com prefixo {@code -}) guardadas para um jogador. */
public record PlayerData(Set<String> groups, Set<String> permissions) {
}
