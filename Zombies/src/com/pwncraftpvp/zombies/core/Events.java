package com.pwncraftpvp.zombies.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Effect;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BlockIterator;

import com.pwncraftpvp.zombies.events.PlayerTargetBlockEvent;
import com.pwncraftpvp.zombies.game.Door;
import com.pwncraftpvp.zombies.game.Perk;
import com.pwncraftpvp.zombies.game.Status;
import com.pwncraftpvp.zombies.game.Weapon;
import com.pwncraftpvp.zombies.game.Window;
import com.pwncraftpvp.zombies.tasks.CountdownTask;
import com.pwncraftpvp.zombies.tasks.MysteryBoxTask;
import com.pwncraftpvp.zombies.tasks.PerkTask;
import com.pwncraftpvp.zombies.tasks.PlayerDeathTask;
import com.pwncraftpvp.zombies.tasks.PlayerHealTask;
import com.pwncraftpvp.zombies.tasks.UpgradeTask;
import com.pwncraftpvp.zombies.tasks.WindowRepairTask;
import com.pwncraftpvp.zombies.utils.EffectUtils;
import com.pwncraftpvp.zombies.utils.Utils;

public class Events implements Listener {
	
	private Main main = Main.getInstance();
	private String gray = ChatColor.GRAY + "";
	private String red = ChatColor.RED + "";
	
	@EventHandler
	public void playerJoin(PlayerJoinEvent event){
		Player player = event.getPlayer();
		ZPlayer zplayer = new ZPlayer(player);
		
		int online = Bukkit.getOnlinePlayers().length;
		int min = Utils.getMinimumPlayers();
		if(online < min){
			int req = min - online;
			zplayer.sendMessage("You are in intermission. The game needs " + red + req + gray + " more " + ((req == 1) ? "player" : "players") + " to begin.");
		}else{
			if(main.game.getStatus() == Status.WAITING){
				CountdownTask task = new CountdownTask();
				task.runTaskTimer(main, 0, 20);
				main.game.votingtask = task;
				main.game.setStatus(Status.VOTING);
				main.game.setVoteables();
			}
		}
		
		player.setHealth(20);
		zplayer.setInventory(main.game.getStatus());
		
		Statistics stats = new Statistics(player);
		stats.pull();
		main.stats.put(player.getName(), stats);
	}
	
	@EventHandler
	public void playerQuit(PlayerQuitEvent event){
		Player player = event.getPlayer();
		ZPlayer zplayer = new ZPlayer(player);
		zplayer.logout();
	}
	
	@EventHandler
	public void playerKick(PlayerKickEvent event){
		Player player = event.getPlayer();
		ZPlayer zplayer = new ZPlayer(player);
		zplayer.logout();
	}
	
	@EventHandler
	public void playerInteract(PlayerInteractEvent event){
		Player player = event.getPlayer();
		ZPlayer zplayer = new ZPlayer(player);
		int slot = player.getInventory().getHeldItemSlot();
		
		if(slot == 0 || slot == 1){
			if(event.getAction() == Action.RIGHT_CLICK_AIR){
				event.setCancelled(true);
				zplayer.shootWeapon(slot);
			}else if(event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK){
				event.setCancelled(true);
				zplayer.reloadWeapon(slot);
			}
		}
		
		if(event.getAction() == Action.RIGHT_CLICK_BLOCK){
			Block block = event.getClickedBlock();
			if(block != null){
				if(block.getType() == Material.IRON_FENCE){
					Door door = null;
					for(Door d : main.game.getAllDoors()){
						if(d.isBlock(block) == true){
							door = d;
							break;
						}
					}
					if(door != null){
						if(zplayer.getScore() >= door.getPrice()){
							for(Block b : door.getBlocks()){
								for(Player p : Bukkit.getOnlinePlayers()){
									p.playEffect(b.getLocation(), Effect.STEP_SOUND, b.getType());
								}
								b.setType(Material.AIR);
							}
							if(main.game.isUnlocked(door.getAreaID()) == false){
								main.game.addUnlockedArea(main.game.getArea(door.getAreaID()));
							}
							zplayer.removeScore(door.getPrice());
							zplayer.sendMessage("You have removed this door for " + red + door.getPrice() + gray + " points.");
						}else{
							zplayer.sendError("Insufficient points.");
						}
					}
				}else if(block.getType() == Material.WALL_SIGN){
					Sign sign = (Sign) block.getState();
					if(sign.getLine(1).contains("Repair") == true && sign.getLine(2).contains("Window") == true){
						if(main.game.repair.containsKey(player.getName()) == false){
							Window window = null;
							for(Window w : main.game.getAllWindows()){
								if(w.getSignLocation().getBlockX() == block.getLocation().getBlockX() && 
										w.getSignLocation().getBlockY() == block.getLocation().getBlockY() && 
										w.getSignLocation().getBlockZ() == block.getLocation().getBlockZ()){
									window = w;
									break;
								}
							}
							if(window != null){
								if(main.game.windowhealth.get(window.getID()) < 6){
									WindowRepairTask task = new WindowRepairTask(player, window);
									task.runTaskTimer(main, 0, 15);
									main.game.repair.put(player.getName(), task);
								}
							}
						}else{
							main.game.repair.get(player.getName()).clicking = true;
						}
					}
				}else if(block.getType() == Material.CHEST){
					event.setCancelled(true);
					boolean box = false;
					for(Location l : main.game.getCurrentMysteryBox().getBlocks()){
						if((l.getBlockX() == block.getLocation().getBlockX() && 
								l.getBlockY() == block.getLocation().getBlockY() && 
								l.getBlockZ() == block.getLocation().getBlockZ()) || l.distance(block.getLocation()) <= 1){
							box = true;
							break;
						}
					}
					if(box == true){
						if(main.game.boxtask == null){
							if(zplayer.getScore() >= 950){
								zplayer.removeScore(950);
								
								main.game.boxtask = new MysteryBoxTask(player, main.game.getCurrentMysteryBox());
								main.game.boxtask.runTaskTimer(main, 0, 7);
								zplayer.sendMessage("You have purchased this box for " + red + 950 + gray + " points.");
							}else{
								zplayer.sendError("Insufficient points.");
							}
						}else if(main.game.boxweapon.containsKey(player.getName()) == true){
							if(player.getInventory().getHeldItemSlot() == 0 || player.getInventory().getHeldItemSlot() == 1){
								zplayer.giveWeapon(main.game.boxweapon.get(player.getName()), false);
								main.game.boxtask.cancelTask(true);
							}else{
								zplayer.sendError("You must be holding a weapon.");
							}
						}
					}
				}else if(block.getType() == Material.ENDER_CHEST){
					event.setCancelled(true);
					if(Utils.areDifferent(block.getLocation(), main.game.getMap().getUpgrade()) == false){
						if(main.game.upgradetask == null){
							if((player.getInventory().getHeldItemSlot() == 0 || player.getInventory().getHeldItemSlot() == 1) && player.getItemInHand() != null && player.getItemInHand().getType() != Material.AIR){
								if(zplayer.getScore() >= 5000){
									if(zplayer.isWeaponUpgraded() == false){
										zplayer.removeScore(5000);
										
										main.game.upgradetask = new UpgradeTask(player, zplayer.getWeaponInHand());
										main.game.upgradetask.runTaskTimer(main, 0, 20);
										player.setItemInHand(null);
										zplayer.sendMessage("You have purchased pack-a-punch for " + red + 5000 + gray + " points.");
									}else{
										zplayer.sendError("You have already upgraded this weapon.");
									}
								}else{
									zplayer.sendError("Insufficient points.");
								}
							}else{
								zplayer.sendError("You must be holding a weapon.");
							}
						}else if(main.game.upgradetask.getPlayer().getName().equalsIgnoreCase(player.getName()) == true){
							if(main.game.upgradetask.isWaiting() == true){
								zplayer.giveWeapon(main.game.upgradetask.getWeapon(), true);
								main.game.upgradetask.cancelTask();
							}
						}
					}
				}else if(block.getType() == Material.WOOL){
					Perk perk = null;
					for(Perk p : Perk.values()){
						if(main.game.getMap().getPerkLocation(p).distance(block.getLocation()) < 2){
							perk = p;
							break;
						}
					}
					if(perk != null){
						if(zplayer.getScore() >= perk.getPrice()){
							if(main.game.perktask.containsKey(player.getName()) == false){
								if(zplayer.hasPerk(perk) == false){
									PerkTask task = new PerkTask(player, perk, player.getInventory().getHeldItemSlot());
									task.runTaskTimer(main, 0, 10);
									main.game.perktask.put(player.getName(), task);
									player.getInventory().setHeldItemSlot(8);
									player.getInventory().setItem(8, Utils.renameItem(new ItemStack(Material.POTION), ChatColor.AQUA + perk.getName()));
								}
							}
						}else{
							zplayer.sendError("Insufficient points.");
						}
					}
				}else{
					zplayer.shootWeapon(slot);
				}
			}
		}
	}
	
	@EventHandler
	public void playerTargetBlock(PlayerTargetBlockEvent event){
		Player player = event.getPlayer();
		ZPlayer zplayer = new ZPlayer(player);
		Block block = event.getNewBlock();
		if(main.game.getStatus() == Status.STARTED){
			if(block.getType() == Material.IRON_FENCE){
				Door door = null;
				for(Door d : main.game.getAllDoors()){
					if(d.isBlock(block) == true){
						door = d;
						break;
					}
				}
				if(door != null){
					zplayer.sendActionBar(gray + "Press right-click to open. [" + red + door.getPrice() + " points" + gray + "]");
				}
			}else if(block.getType() == Material.WALL_SIGN){
				Sign sign = (Sign) block.getState();
				if(sign.getLine(1).contains("Repair") == true && sign.getLine(2).contains("Window") == true){
					Window window = null;
					for(Window w : main.game.getAllWindows()){
						if(w.getSignLocation().getBlockX() == block.getLocation().getBlockX() && 
								w.getSignLocation().getBlockY() == block.getLocation().getBlockY() && 
								w.getSignLocation().getBlockZ() == block.getLocation().getBlockZ()){
							window = w;
							break;
						}
					}
					if(window != null){
						if(main.game.windowhealth.get(window.getID()) < 6){
							zplayer.sendActionBar(gray + "Hold right-click to repair.");
						}
					}
				}
			}else if(block.getType() == Material.CHEST){
				boolean box = false;
				for(Location l : main.game.getCurrentMysteryBox().getBlocks()){
					if(Utils.areDifferent(l, block.getLocation()) == false || l.distance(block.getLocation()) <= 1){
						box = true;
						break;
					}
				}
				if(box == true){
					if(main.game.boxtask == null){
						zplayer.sendActionBar(gray + "Press right-click for a random weapon. [" + red + "950 points" + gray + "]");
					}else if(main.game.boxweapon.containsKey(player.getName()) == true){
						zplayer.sendActionBar(ChatColor.GRAY + "Press right-click to trade weapons. [" + ChatColor.RED + main.game.boxweapon.get(player.getName()).getName() + ChatColor.GRAY + "]");
					}
				}
			}else if(block.getType() == Material.ENDER_CHEST){
				if(Utils.areDifferent(block.getLocation(), main.game.getMap().getUpgrade()) == false){
					if(main.game.upgradetask == null){
						if(zplayer.isWeaponUpgraded() == false){
							zplayer.sendActionBar(gray + "Press right-click to upgrade weapon. [" + red + "5000 points" + gray + "]");
						}
					}else if(main.game.upgradetask.getPlayer().getName().equalsIgnoreCase(player.getName()) == true){
						if(main.game.upgradetask.isWaiting() == true){
							zplayer.sendActionBar(ChatColor.GRAY + "Press right-click to accept weapon. [" + ChatColor.RED + main.game.upgradetask.getWeapon().getUpgradedName() + ChatColor.GRAY + "]");
						}
					}
				}
			}else if(block.getType() == Material.WOOL){
				Perk perk = null;
				for(Perk p : Perk.values()){
					if(main.game.getMap().getPerkLocation(p).distance(block.getLocation()) < 2){
						perk = p;
						break;
					}
				}
				if(perk != null){
					if(main.game.perktask.containsKey(player.getName()) == false){
						if(zplayer.hasPerk(perk) == false){
							zplayer.sendActionBar(ChatColor.GRAY + "Press right-click to purchase " + perk.getName() + ". [" + ChatColor.RED + perk.getPrice() + " points" + ChatColor.GRAY + "]");
						}
					}
				}
			}
		}
	}
	
	@EventHandler
	public void entityDamageByEntity(EntityDamageByEntityEvent event){
		if(event.getEntity() instanceof Zombie || event.getEntity() instanceof Wolf){
			LivingEntity entity = (LivingEntity) event.getEntity();
			entity.setNoDamageTicks(0);
			if(event.getDamager() instanceof Egg){
				Egg egg = (Egg) event.getDamager();
				if(egg.getShooter() != null && egg.getShooter() instanceof Player){
					Player player = (Player) egg.getShooter();
					ZPlayer zplayer = new ZPlayer(player);
					Weapon weapon = zplayer.getWeaponInHand();
					if(weapon != null){
						boolean upgraded = zplayer.isWeaponUpgraded();
						double damage = weapon.getDamage(upgraded);
						
						boolean headshot = false;
						if(egg.getLocation().getY() - entity.getLocation().getY() > 1.4){
							damage = weapon.getHeadshotDamage(upgraded);
							headshot = true;
						}
						
						EffectUtils.playBloodEffect(entity, headshot);
						event.setCancelled(true);
						
						double newhealth = entity.getHealth() - damage;
						if(newhealth > 1){
							entity.setHealth(newhealth);
							Utils.setNavigation(entity, player.getLocation());
							entity.playEffect(EntityEffect.HURT);
							zplayer.addScore(10);
						}else{
							main.game.killEntity(entity);
							zplayer.giveBrains(1);
							if(headshot == false){
								zplayer.addScore(60);
							}else{
								zplayer.addScore(100);
							}
						}
					}
				}
			}else{
				event.setCancelled(true);
			}
		}else if(event.getEntity() instanceof Player){
			Player player = (Player) event.getEntity();
			ZPlayer zplayer = new ZPlayer(player);
			if(event.getDamager().getType() == EntityType.ZOMBIE || event.getDamager().getType() == EntityType.WOLF){
				if(main.game.ending == false){
					final LivingEntity damager = (LivingEntity) event.getDamager();
					if(main.game.nodamage.contains(damager.getEntityId()) == false){
						if(!main.game.death.containsKey(player.getName())){
							double damage = 12;
							int delay = 40;
							if(event.getDamager().getType() == EntityType.WOLF){
								damage = 3.5;
								delay = 15;
							}
							if(zplayer.hasPerk(Perk.JUGGERNOG) == true){
								damage = damage / 2.5;
							}
							event.setDamage(damage);
							double newhealth = player.getHealth() - damage;
							if(newhealth < 1){
								zplayer.toggleDead(true, false);
								event.setCancelled(true);
								player.damage(0);
								player.setHealth(1);
								if(main.game.heal.containsKey(player.getName()) == true){
									main.game.heal.get(player.getName()).cancelTask(false);
									main.game.heal.remove(player.getName());
								}
							}else{
								if(main.game.heal.containsKey(player.getName()) == true){
									main.game.heal.get(player.getName()).runtime = 0;
								}else{
									PlayerHealTask task = new PlayerHealTask(player);
									task.runTaskTimer(main, 0, 20);
									main.game.heal.put(player.getName(), task);
								}
							}
							main.game.nodamage.add(damager.getEntityId());
							main.getServer().getScheduler().scheduleSyncDelayedTask(main, new Runnable(){
								public void run(){
									main.game.nodamage.remove(new Integer(damager.getEntityId()));
								}
							}, delay);
						}else{
							event.setCancelled(true);
							Utils.navigateToNearest(damager);
						}
					}else{
						event.setCancelled(true);
					}
				}else{
					event.setCancelled(true);
				}
			}else{
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	public void playerInteractEntity(PlayerInteractEntityEvent event){
		Player player = event.getPlayer();
		ZPlayer zplayer = new ZPlayer(player);
		if(event.getRightClicked() instanceof Player){
			Player target = (Player) event.getRightClicked();
			if(main.game.death.containsKey(target.getName())){
				PlayerDeathTask task = main.game.death.get(target.getName());
				task.zreviver = zplayer;
				task.reviving = true;
				task.setHologramText(false);
				task.clicks++;
			}
		}
	}
	
	@EventHandler
	public void entityDamage(EntityDamageEvent event){
		if(event.getCause() == DamageCause.FIRE || event.getCause() == DamageCause.FIRE_TICK){
			event.setCancelled(true);
			if(event.getEntity().getType() == EntityType.ZOMBIE){
				event.getEntity().setFireTicks(0);
			}
		}
	}
	
	@EventHandler
	public void playerItemHeld(PlayerItemHeldEvent event){
		Player player = event.getPlayer();
		ZPlayer zplayer = new ZPlayer(player);
		int slot = event.getNewSlot();
		
		if(main.game.perktask.containsKey(player.getName()) == true){
			event.setCancelled(true);
		}else{
			if(main.game.reload.containsKey(player.getName()) == true){
				main.game.reload.get(player.getName()).cancelTask(false);
				main.game.reload.remove(player.getName());
			}
			
			Weapon weapon = zplayer.getWeaponInSlot(slot);
			if(weapon != null){
				zplayer.sendAmmo(zplayer.getAmmo(slot, weapon));
			}else{
				zplayer.sendActionBar("");
			}
		}
	}
	
	@EventHandler
	public void playerMove(PlayerMoveEvent event){
		Player player = event.getPlayer();
		if(main.game.death.containsKey(player.getName())){
			if(event.getTo().getX() != event.getFrom().getX() || event.getTo().getZ() != event.getFrom().getZ()){
				player.teleport(event.getFrom());
			}
		}
	}
	
	@SuppressWarnings("deprecation")
	@EventHandler
	public void projectileHit(ProjectileHitEvent event){
		Entity entity = event.getEntity();
		BlockIterator iterator = new BlockIterator(entity.getWorld(), entity.getLocation().toVector(), entity.getVelocity().normalize(), 0, 4);
		Block hitBlock = null;
		
		while(iterator.hasNext()) {
			hitBlock = iterator.next();
			if(hitBlock.getTypeId() != 0){
				break;
			}
		}
		
		if(hitBlock != null){
			for(Entity e : entity.getNearbyEntities(20, 20, 20)){
				if(e instanceof Player){
					Player p = (Player) e;
					p.playEffect(hitBlock.getLocation(), Effect.STEP_SOUND, hitBlock.getTypeId());
				}
			}
		}
	}
	
	@EventHandler
	public void inventoryClick(InventoryClickEvent event){
		if(event.getWhoClicked() instanceof Player){
			Player player = (Player) event.getWhoClicked();
			if(player.isOp() == false){
				event.setCancelled(true);
			}
		}
	}
	
	@EventHandler
	public void creatureSpawn(CreatureSpawnEvent event){
		if(event.getSpawnReason() != SpawnReason.CUSTOM){
			event.setCancelled(true);
		}
	}
	
	@EventHandler
	public void entityTarget(EntityTargetEvent event){
		if(event.getEntity() instanceof Zombie && event.getTarget() instanceof Player){
			Player player = (Player) event.getTarget();
			if(main.game.death.containsKey(player.getName()) == true){
				event.setCancelled(true);
				Player nearest = Utils.navigateToNearest((LivingEntity) event.getEntity());
				if(nearest == null){
					event.setTarget(null);
				}else{
					event.setTarget(nearest);
				}
			}
		}
	}
	
	@EventHandler
	public void foodLevelChange(FoodLevelChangeEvent event){
		if(event.getEntity() instanceof Player){
			Player player = (Player) event.getEntity();
			player.setFoodLevel(20);
		}
		event.setCancelled(true);
	}
	
	@EventHandler
	public void playerDropItem(PlayerDropItemEvent event){
		event.setCancelled(true);
	}
	
	@EventHandler
	public void playerPickupItem(PlayerPickupItemEvent event){
		event.getItem().remove();
		event.setCancelled(true);
	}

}