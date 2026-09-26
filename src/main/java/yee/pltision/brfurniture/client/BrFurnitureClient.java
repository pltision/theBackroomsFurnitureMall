package yee.pltision.brfurniture.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import yee.pltision.brfurniture.BrFurniture;

/**
 * 客户端入口。这个类在专用服务器上不会被加载，所以可以安全地引用客户端类。
 *
 * <p>它做两件事：</p>
 * <ol>
 *     <li>注册配置界面：方块列表有问题时先显示 {@link BlockWarningsScreen}，
 *         没问题时就是 NeoForge 默认的 {@link ConfigurationScreen}；</li>
 *     <li>客户端启动日志。</li>
 * </ol>
 */
@Mod(value = BrFurniture.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = BrFurniture.MODID, value = Dist.CLIENT)
public class BrFurnitureClient {
    public BrFurnitureClient(ModContainer container) {
        // 入口是"模组列表 → 后室家具店 → 配置"。
        // IConfigScreenFactory 是 NeoForge 公开的扩展点，这里用它的自定义实现来插入警告页。
        container.registerExtensionPoint(IConfigScreenFactory.class, (modContainer, modListScreen) -> {
            if (BrFurniture.blockWarnings().isEmpty()) {
                return new ConfigurationScreen(modContainer, modListScreen);
            }
            return new BlockWarningsScreen(modListScreen, BrFurniture.blockWarnings());
        });
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        BrFurniture.LOGGER.info("BRFURNITURE CLIENT SETUP");
        BrFurniture.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }
}
