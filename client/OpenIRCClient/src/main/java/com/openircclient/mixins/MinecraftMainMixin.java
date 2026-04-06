package com.openircclient.mixins;

import com.openircclient.Openircclient;
import com.openircclient.windows.LoginWindow;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public class MinecraftMainMixin {

    @Inject(at = @At("HEAD"), method = "main", remap = false)
    private static void onGameStart(String[] args, CallbackInfo ci) {
        System.setProperty("java.awt.headless", "false");
        Openircclient.initConnection();
        LoginWindow.showBlocking();
    }
}