package com.skalpha.tiertagger.mixin;

import com.skalpha.tiertagger.TierTagger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.DisplayRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(DisplayRenderer.TextDisplayRenderer.class)
public class MixinTextDisplayRenderer {
    @ModifyArg(method = "splitLines", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"), index = 0)
    public FormattedText editText(FormattedText original) {
        final Component text = (Component) original;
        final String stringText = text.getString();
        final ClientLevel level = Minecraft.getInstance().level;

        if (!stringText.isBlank() && level != null) {
            for (Player player : level.players()) {
                int index = stringText.indexOf(player.getScoreboardName());
                if (!isSurrounded(stringText, index, player.getScoreboardName().length())) {
                    return TierTagger.appendTier(player.getUUID(), text);
                }
            }
        }

        return original;
    }


    // 2024 edit: i have no fucking clue what this does but sure uku3lig from the past, slay queen
    @Unique
    private boolean isSurrounded(String stringText, int index, int length) {
        return index == -1 || // not found
                (index > 0 && Character.isLetterOrDigit(stringText.charAt(index - 1))) ||
                (index + length < stringText.length() && Character.isLetterOrDigit(stringText.charAt(index + length)));
    }
}
