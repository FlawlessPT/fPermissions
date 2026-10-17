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
- [ ] `Bukkit.broadcast(msg, perm)`/`broadcastMessage` usam as subscrições do `PluginManager`, que só o `PermissibleBase` original cria: alertas de staff de plugins de terceiros não chegam a admins por grupo. Corrigir chamando `PluginManager.subscribeToPermission` para as permissões do jogador ao injetar/atualizar e `unsubscribe` ao repor. Nenhum plugin nosso usa isto hoje.
- [ ] `runTask` depois do plugin desativado lança `IllegalPluginAccessException` (ruído no log).
- [ ] Se a BD estiver em baixo no arranque o plugin desativa-se e os jogadores ficam com as permissões por omissão do Bukkit (fail-open ao nível do plugin).

## Qualidade
- [ ] O teste de `restoreAll` usa reflexão no campo privado `held`.
- [ ] `corpoNuloValeGrupoVazio` tem um nome que exagera.
- [ ] Testes de `GroupsLoader` só com `MemoryConfiguration`.
- [ ] Teste do `PermissibleInjector` só com classes falsas: a reflexão em CraftBukkit real nunca correu, verificar no servidor.
- [ ] `Collation` implícita (case-insensitive) das tabelas.
- [ ] `GroupCommand` e `PermCommand` sem testes unitários.

## Operação
- [ ] JDK 26+ (JEP 500) avisa e depois bloqueia escrever campos `final` por reflexão (`CraftHumanEntity.perm` é `protected final`).
- [ ] Verificação no servidor real por fazer: `stop` e arranque, `/grupo add <nome> admin` na consola, `/perm` como não-admin, `/grupo reload`, `/reload` com jogadores online, sair e voltar a entrar, uma RECONEXÃO com a sessão ainda ligada, e `/tell` e `/help` como membro.
