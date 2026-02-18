package com.ihanuat.mod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Ihanuat implements ModInitializer {
        public static final Logger LOGGER = LoggerFactory.getLogger("ihanuat");

        @Override
        public void onInitialize() {
                LOGGER.info("Ihanuat v1 Initialized!");
        }
}
