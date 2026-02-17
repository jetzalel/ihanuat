package com.example.mod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod implements ModInitializer {
        public static final Logger LOGGER = LoggerFactory.getLogger("example-mod");

        @Override
        public void onInitialize() {
                LOGGER.info("Example Mod Initialized!");
        }
}
