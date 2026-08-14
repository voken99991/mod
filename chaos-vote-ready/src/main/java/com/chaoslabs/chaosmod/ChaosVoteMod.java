package com.chaoslabs.chaosmod;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChaosVoteMod implements ModInitializer {
    public static final String MOD_ID = "chaosvote";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        new ChaosVoteManager();
        LOGGER.info("Chaos Vote loaded. Let the bad decisions begin.");
    }
}
