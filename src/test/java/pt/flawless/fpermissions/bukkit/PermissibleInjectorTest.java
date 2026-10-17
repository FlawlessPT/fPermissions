package pt.flawless.fpermissions.bukkit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.bukkit.permissions.PermissibleBase;
import org.junit.jupiter.api.Test;

class PermissibleInjectorTest {

    /** PermissibleBase sem Bukkit.getServer(), que o construtor real exige. */
    static class Inert extends PermissibleBase {
        Inert() {
            super(null);
        }

        @Override
        public void recalculatePermissions() {
        }
    }

    static class FakeHuman {
        protected PermissibleBase perm = new Inert();
    }

    static class FakePlayer extends FakeHuman {
    }

    static class FinalHuman {
        private final PermissibleBase perm = new Inert();
    }

    static class FinalPlayer extends FinalHuman {
    }

    static class NoField {
    }

    private final PermissibleInjector injector = new PermissibleInjector(null);

    private static PermissibleBase perm(Object o) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField("perm");
                f.setAccessible(true);
                return (PermissibleBase) f.get(o);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new AssertionError();
    }

    @Test
    void injectReplacesAndRestoreReturnsOriginal() throws Exception {
        FakePlayer p = new FakePlayer();
        PermissibleBase original = p.perm;
        UUID id = UUID.randomUUID();
        injector.inject(p, id);
        assertTrue(p.perm instanceof InjectedPermissible);
        injector.restore(p, id);
        assertSame(original, p.perm);
    }

    @Test
    void privateFinalFieldInSuperclass() throws Exception {
        FinalPlayer p = new FinalPlayer();
        PermissibleBase original = perm(p);
        UUID id = UUID.randomUUID();
        injector.inject(p, id);
        assertNotSame(original, perm(p));
        injector.restore(p, id);
        assertSame(original, perm(p));
    }

    @Test
    void secondInjectKeepsTrueOriginal() {
        FakePlayer p = new FakePlayer();
        PermissibleBase original = p.perm;
        UUID id = UUID.randomUUID();
        injector.inject(p, id);
        injector.inject(p, id);
        injector.restore(p, id);
        assertSame(original, p.perm);
    }

    @Test
    void restoreWithoutInjectIsNoop() {
        FakePlayer p = new FakePlayer();
        PermissibleBase original = p.perm;
        injector.restore(p, UUID.randomUUID());
        assertSame(original, p.perm);
    }

    @Test
    void restoreAllRestoresEveryone() {
        FakePlayer a = new FakePlayer();
        FakePlayer b = new FakePlayer();
        PermissibleBase oa = a.perm;
        PermissibleBase ob = b.perm;
        injector.inject(a, UUID.randomUUID());
        injector.inject(b, UUID.randomUUID());
        injector.restoreAll();
        assertSame(oa, a.perm);
        assertSame(ob, b.perm);
    }

    @Test
    @SuppressWarnings("unchecked")
    void restoreAllContinuesAfterFailure() throws Exception {
        FakePlayer good = new FakePlayer();
        PermissibleBase original = good.perm;
        injector.inject(good, UUID.randomUUID());
        // entrada inválida (alvo de tipo errado): o set do campo lança IllegalArgumentException
        java.lang.reflect.Field heldField = PermissibleInjector.class.getDeclaredField("held");
        heldField.setAccessible(true);
        java.util.Map<UUID, Object> held = (java.util.Map<UUID, Object>) heldField.get(injector);
        var ctor = Class.forName(PermissibleInjector.class.getName() + "$Held").getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        java.lang.reflect.Field perm = FakeHuman.class.getDeclaredField("perm");
        perm.setAccessible(true);
        for (int i = 0; i < 5; i++) { // vários maus, para a ordem de iteração não importar
            held.put(UUID.randomUUID(), ctor.newInstance(new NoField(), perm, new Inert()));
        }
        injector.restoreAll();
        assertSame(original, good.perm);
    }

    @Test
    void missingFieldThrows() {
        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> injector.inject(new NoField(), UUID.randomUUID()));
        assertTrue(e.getMessage().contains("perm"));
    }

    @Test
    void staleRestoreOfOldTargetKeepsNewInjection() {
        FakePlayer oldSession = new FakePlayer();
        FakePlayer newSession = new FakePlayer();
        UUID id = UUID.randomUUID();
        injector.inject(oldSession, id);
        injector.inject(newSession, id); // reconnect: mesmo UUID, outro objeto
        assertTrue(newSession.perm instanceof InjectedPermissible);
        injector.restore(oldSession, id);
        assertTrue(newSession.perm instanceof InjectedPermissible);
        injector.restoreAll();
        assertFalse(newSession.perm instanceof InjectedPermissible);
    }
}
