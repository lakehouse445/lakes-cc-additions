package gg.lakehouse.scanner;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.LuaTable;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.pocket.IPocketAccess;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPosition;
import dan200.computercraft.shared.peripheral.speaker.UpgradeSpeakerPeripheral;
import dan200.computercraft.shared.pocket.peripherals.PocketModemPeripheral;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

/**
 * The "everything a pocket computer can carry" peripheral: an ender modem
 * (interdimensional, unlimited range) fused with a speaker.
 *
 * Extends CC:T's pocket ender modem, so all modem methods (open, close,
 * transmit, rednet compatibility...) are inherited. Speaker methods are
 * delegated to an embedded speaker instance. Reports "speaker" as an
 * additional peripheral type, so both peripheral.find("modem") and
 * peripheral.find("speaker") locate it.
 *
 * NOTE: uses CC:T internal classes (shared.*), pinned to CC:T 1.119.0.
 * If CC:T is ever updated, re-check these classes against its source.
 */
public class MultitoolPeripheral extends PocketModemPeripheral {
    private final IPocketAccess access;
    private final MultitoolSpeaker speaker;
    private int lastLight = -1;

    public MultitoolPeripheral(IPocketAccess access) {
        super(true, access); // true = advanced = ender modem
        this.access = access;
        this.speaker = new MultitoolSpeaker(access);
    }

    @Override
    public Set<String> getAdditionalTypes() {
        return Set.of("speaker");
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return this == other;
    }

    @Override
    public void attach(IComputerAccess computer) {
        super.attach(computer);
        speaker.attach(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        speaker.detach(computer);
        super.detach(computer);
    }

    // ------------------------------------------------------------------
    // Speaker delegation (signatures mirror SpeakerPeripheral exactly)
    // ------------------------------------------------------------------

    @LuaFunction
    public final boolean playNote(ILuaContext context, String instrument,
                                  Optional<Double> volume, Optional<Double> pitch) throws LuaException {
        return speaker.playNote(context, instrument, volume, pitch);
    }

    @LuaFunction
    public final boolean playSound(ILuaContext context, String name,
                                   Optional<Double> volume, Optional<Double> pitch) throws LuaException {
        return speaker.playSound(context, name, volume, pitch);
    }

    @LuaFunction(unsafe = true)
    public final boolean playAudio(ILuaContext context, LuaTable<?, ?> audio,
                                   Optional<Double> volume) throws LuaException {
        return speaker.playAudio(context, audio, volume);
    }

    @LuaFunction
    public final void stop() {
        speaker.stop();
    }

    // ------------------------------------------------------------------
    // Ticking (called from MultitoolUpgrade.update every tick)
    // ------------------------------------------------------------------

    void serverTick() {
        speaker.update();

        // Item light: blue while making sound, red while a channel is open
        int light = speaker.madeSound() ? 0x3320FC
            : getModemState().isOpen() ? 0xBA0000
            : -1;
        if (light != lastLight) {
            lastLight = light;
            access.setLight(light);
        }
    }

    /** Speaker that follows the pocket computer's holder, without fighting over the item light. */
    private static final class MultitoolSpeaker extends UpgradeSpeakerPeripheral {
        private final IPocketAccess access;

        MultitoolSpeaker(IPocketAccess access) {
            this.access = access;
        }

        @Override
        public SpeakerPosition getPosition() {
            var entity = access.getEntity();
            return entity == null
                ? SpeakerPosition.of(access.getLevel(), access.getPosition())
                : SpeakerPosition.of(entity);
        }

        @Override
        public boolean equals(@Nullable IPeripheral other) {
            return this == other;
        }
    }
}
