# TODO — fPermissions

Pendências conhecidas deste plugin (17/10/2026). Riscar quando feito. Detalhes em `docs/superpowers/specs/2026-10-17-fpermissions-design.md` (no monorepo).

## Funcionalidades
- [ ] Grupos com prazo (temporários): decidir se ficam neste plugin ou noutro.
- [ ] Prefixos e chat: noutro plugin que consuma a `PermissionsApi`.
- [ ] Sincronização de permissões entre vários servidores: não há notificação entre instâncias.
- [ ] `getEffectivePermissions` só lista literais (sem expandir curingas).
- [ ] Permissões dadas por `addAttachment` de outros plugins são ignoradas.
- [ ] `Bukkit.getOfflinePlayer(name)` assume `online-mode=false` (UUIDs derivados do nome): mudar para online-mode exige refazer as atribuições.
- [ ] Renomear `barberians.staff` no fUtilities para um nome do namespace e declarar as permissões dos `plugin.yml`: hoje `default: op`/livres deixam de contar com o fPermissions, só o que está nos grupos conta.

## Código
- [ ] Cache: a entrada fica na cache quando OUTRO plugin recusa o pré-login depois de o nosso carregar o jogador. Acrescentar handler MONITOR em `AsyncPlayerPreLoginEvent` que faz unload se `getLoginResult() != ALLOWED`.
- [ ] `runTask` depois do plugin desativado lança `IllegalPluginAccessException` (ruído no log).
- [ ] Se a BD estiver em baixo no arranque o plugin desativa-se e os jogadores ficam com as permissões por omissão do Bukkit (fail-open ao nível do plugin).

## Qualidade
- [ ] `PlayerListener.onLogin` (ALLOWED vs não) sem teste.
- [ ] O teste de `restoreAll` usa reflexão no campo privado `held`.
- [ ] `corpoNuloValeGrupoVazio` tem um nome que exagera.
- [ ] Testes de `GroupsLoader` só com `MemoryConfiguration`.
- [ ] Teste do `PermissibleInjector` só com classes falsas: a reflexão em CraftBukkit real nunca correu, verificar no servidor.
- [ ] `Collation` implícita (case-insensitive) das tabelas.
- [ ] `GroupCommand` e `PermCommand` sem testes unitários.

## Operação
- [ ] Verificação manual no servidor por fazer (ver `CLAUDE.md`, secção Estado).
