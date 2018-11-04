/*
 * Copyright 2018 Benjamin Martin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.lapismc.afkplus.playerdata;

import me.kangarko.compatbridge.model.CompSound;
import net.lapismc.afkplus.AFKPlus;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

@SuppressWarnings("WeakerAccess")
public class AFKPlusPlayer {

    private AFKPlus plugin;
    private UUID uuid;
    private Long lastInteract;
    private Long afkStart;
    private boolean isAFK;
    private boolean isWarned;

    public AFKPlusPlayer(AFKPlus plugin, UUID uuid) {
        this.plugin = plugin;
        this.uuid = uuid;
        isAFK = false;
        isWarned = false;
        lastInteract = System.currentTimeMillis();
    }

    /**
     * Get the players username
     *
     * @return Returns the name of the player
     */
    public String getName() {
        return Bukkit.getOfflinePlayer(uuid).getName();
    }

    /**
     * Check if the player is permitted to do something
     *
     * @param perm The permission you wish to check
     * @return Returns true if the player DOESN'T have the permission
     */
    public boolean isNotPermitted(Permission perm) {
        return !plugin.permissions.isPermitted(uuid, perm.getPermission());
    }

    /**
     * Warn the player with a message and sound if enabled
     */
    public void warnPlayer() {
        isWarned = true;
        Player p = Bukkit.getPlayer(uuid);
        //Send the player the warning message
        p.sendMessage(plugin.config.getMessage("Warning"));
        //Check if warning sounds are enabled
        if (!plugin.getConfig().getString("WarningSound").equals("")) {
            //Get the sound from Compat Bridge
            Sound sound = CompSound.convert(plugin.getConfig().getString("WarningSound"));
            //Play the sound at the players current location
            p.playSound(p.getLocation(), sound, 1, 1);
        }
    }

    /**
     * Check if the player is AFK
     *
     * @return returns true if the player is currently AFK
     */
    public boolean isAFK() {
        return isAFK;
    }

    /**
     * Get the system time when the player became AFK
     *
     * @return Returns the System.currentTimeMillis() when the player was set AFK
     */
    public Long getAFKStart() {
        return afkStart;
    }

    /**
     * Starts AFK for this player with a broadcast, Use {@link #forceStartAFK()} for silent AFK
     */
    public void startAFK() {
        //Broadcast the AFK start message
        String message = plugin.config.getMessage("Broadcast.Start")
                .replace("%PLAYER%", getName());
        Bukkit.broadcastMessage(message);
        //Start the AFK
        forceStartAFK();
    }

    /**
     * Silently starts AFK for this player
     */
    public void forceStartAFK() {
        //Record the time that the player was set AFK
        afkStart = System.currentTimeMillis();
        //Set the player as AFK
        isAFK = true;
    }

    /**
     * Stops AFK for this player with a broadcast, Use {@link #forceStopAFK()} for a silent stop
     */
    public void stopAFK() {
        String message = plugin.config.getMessage("Broadcast.Stop")
                .replace("%PLAYER%", getName());
        Bukkit.broadcastMessage(message);
        forceStopAFK();
    }

    /**
     * Silently stops AFK for this player
     */
    public void forceStopAFK() {
        isWarned = false;
        isAFK = false;
        interact();
    }

    /**
     * Runs the action command on this player
     */
    public void takeAction() {
        String command = plugin.getConfig().getString("Action").replace("[PLAYER]", Bukkit.getPlayer(uuid).getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    /**
     * Log an interact, used by events for tracking when the player last did something
     * This will stop AFK if a player is AFK and update the lastInteract value
     */
    public void interact() {
        lastInteract = System.currentTimeMillis();
        if (isAFK) {
            stopAFK();
        }
    }

    /**
     * This is the runnable that detects players who need to be set as AFK, warned or acted upon
     * It is run every second by default
     * This should not be used else where
     *
     * @return Returns the runnable used for AFK detection
     */
    public Runnable getRepeatingTask() {
        return () -> {
            if (Bukkit.getOfflinePlayer(uuid).isOnline()) {
                if (isAFK) {
                    //Get the values that need to be met for warnings and action
                    Integer timeToWarning = plugin.permissions.getPermissionValue(uuid, Permission.TimeToWarning.getPermission());
                    Integer timeToAction = plugin.permissions.getPermissionValue(uuid, Permission.TimeToAction.getPermission());
                    //Get the number of seconds since the player went AFK
                    Long secondsSinceAFKStart = (afkStart - System.currentTimeMillis()) / 1000;
                    //Don't check if we need to warn the player if waring is disabled
                    if (!timeToWarning.equals(-1)) {
                        //Check for warning
                        if (!isWarned && secondsSinceAFKStart >= timeToWarning) {
                            warnPlayer();
                        }
                    }
                    //Check if the player can have an action taken
                    if (!timeToAction.equals(-1)) {
                        //Check for action
                        if (secondsSinceAFKStart >= timeToAction) {
                            takeAction();
                        }
                    }
                } else {
                    Integer timeToAFK = plugin.permissions.getPermissionValue(uuid, Permission.TimeToAFK.getPermission());
                    if (timeToAFK.equals(-1)) {
                        //This allows player to only be put into AFK by commands
                        return;
                    }
                    //Get the number of seconds since the last recorded interact
                    Long secondsSinceLastInteract = (lastInteract - System.currentTimeMillis()) / 1000;
                    //Set them as AFK if it is the same or longer than the time to AFK
                    if (secondsSinceLastInteract.intValue() >= timeToAFK) {
                        startAFK();
                    }
                }
            }
        };
    }
}
