package yee.pltision.brfurniture.client;

import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import yee.pltision.brfurniture.BrFurniture;

/**
 * 进世界后用聊天栏提示一次方块列表的问题。
 *
 * <p>这是"警告界面之外的第二条路"：玩家可能根本不打开模组列表，所以进入世界时
 * 在聊天栏给一条摘要，并指向配置界面看详情。只提示一次，不会每次进世界都刷屏。</p>
 */
@EventBusSubscriber(modid = BrFurniture.MODID, value = Dist.CLIENT)
public final class BrPlayerNotifier {
    private static boolean notified;

    private BrPlayerNotifier() {}

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        int count = BrFurniture.blockWarnings().size();
        if (notified || count == 0) {
            return;
        }
        notified = true;

        var player = event.getPlayer();
        if (player == null) {
            return;
        }
        player.displayClientMessage(Component.translatable("brfurniture.warningscreen.chat_prefix")
                .append(Component.translatable("brfurniture.warningscreen.chat_count", count)), false);
        BrFurniture.LOGGER.info("[BrFM] 已在聊天栏提示方块列表的 {} 个问题", count);
    }
}
