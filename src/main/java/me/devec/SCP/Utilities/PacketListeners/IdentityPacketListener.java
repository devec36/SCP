package me.devec.SCP.Utilities.PacketListeners;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import me.devec.SCP.SCPs.ObsidianKnife;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class IdentityPacketListener implements PacketListener {

    private final ObsidianKnife knife;

    public IdentityPacketListener(ObsidianKnife knife) {
        this.knife = knife;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(event);

            if (!packet.getActions().contains(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER)) {
                return;
            }

            List<WrapperPlayServerPlayerInfoUpdate.PlayerInfo> modifiedEntries = new ArrayList<>();
            boolean altered = false;

            for (WrapperPlayServerPlayerInfoUpdate.PlayerInfo info : packet.getEntries()) {
                UserProfile originalProfile = info.getGameProfile();
                if (originalProfile == null) {
                    modifiedEntries.add(info);
                    continue;
                }

                Player trackingTarget = Bukkit.getPlayer(originalProfile.getUUID());

                if (trackingTarget != null && knife.getIdentityName(trackingTarget) != null) {
                    String fakeName = knife.getIdentityName(trackingTarget);

                    // 1. Visible Fake Entry (Controls Skin and Overhead Name)
                    UserProfile spoofedProfile = new UserProfile(originalProfile.getUUID(), fakeName);
                    List<TextureProperty> textures = originalProfile.getTextureProperties();
                    if (textures != null && !textures.isEmpty()) {
                        spoofedProfile.setTextureProperties(textures);
                    }

                    WrapperPlayServerPlayerInfoUpdate.PlayerInfo spoofedInfo = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                            spoofedProfile,
                            info.isListed(),
                            info.getLatency(),
                            info.getGameMode(),
                            info.getDisplayName(),
                            info.getChatSession()
                    );
                    modifiedEntries.add(spoofedInfo);

                    // 2. Hidden Real Entry (Forces Client-Side Tab Auto-Complete to work)
                    // We generate a random UUID so the client stores it as a completely separate entity.
                    UUID fakeUUID = UUID.randomUUID();
                    UserProfile fallbackProfile = new UserProfile(fakeUUID, originalProfile.getName());

                    WrapperPlayServerPlayerInfoUpdate.PlayerInfo fallbackRealInfo = new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                            fallbackProfile,
                            false, // listed = false hides it completely from the TAB menu list
                            info.getLatency(),
                            info.getGameMode(),
                            info.getDisplayName(),
                            info.getChatSession()
                    );
                    modifiedEntries.add(fallbackRealInfo);

                    altered = true;
                } else {
                    modifiedEntries.add(info);
                }
            }

            if (altered) {
                packet.setEntries(modifiedEntries);
            }
        }
    }
}