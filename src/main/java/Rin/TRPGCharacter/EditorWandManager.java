package Rin.TRPGCharacter;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** EDITOR WAND placement/debug modes. Fix10: event-area selection and editor visualization. */
public class EditorWandManager implements Listener {
    private final Plugin plugin; private final NamespacedKey key;
    private final Map<UUID,String> areaEvent=new HashMap<>(); private final Map<UUID,Location> pos1=new HashMap<>(); private final Set<UUID> debug=new HashSet<>();
    public EditorWandManager(Plugin plugin){this.plugin=plugin;key=new NamespacedKey(plugin,"editor_wand");Bukkit.getScheduler().runTaskTimer(plugin,this::drawDebug,20,20);}
    private boolean wand(ItemStack i){if(i==null||!i.hasItemMeta())return false;Byte b=i.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.BYTE);return b!=null&&b==1;}
    public boolean isSelecting(Player p){ return areaEvent.containsKey(p.getUniqueId()); }
    public void beginAreaSelection(Player p,String eventId){areaEvent.put(p.getUniqueId(),eventId);pos1.remove(p.getUniqueId());p.sendMessage(ChatColor.AQUA+"[EDITOR] エリア設定: 左クリック=地点A / 右クリック=地点B。ブロックをWANDで選択してください。");}
    public void toggleDebug(Player p){if(!debug.add(p.getUniqueId())){debug.remove(p.getUniqueId());p.sendMessage(ChatColor.GRAY+"Editor View: OFF");}else p.sendMessage(ChatColor.AQUA+"Editor View: ON（イベント範囲を可視化）");}
    @EventHandler public void interact(PlayerInteractEvent e){Player p=e.getPlayer();if(!wand(e.getItem()))return;String id=areaEvent.get(p.getUniqueId());if(id==null)return;if(e.getClickedBlock()==null)return;
        e.setCancelled(true);Location l=e.getClickedBlock().getLocation();if(e.getAction()==Action.LEFT_CLICK_BLOCK){pos1.put(p.getUniqueId(),l);p.sendMessage(ChatColor.YELLOW+"地点A: "+fmt(l));}
        else if(e.getAction()==Action.RIGHT_CLICK_BLOCK){Location a=pos1.get(p.getUniqueId());if(a==null){p.sendMessage(ChatColor.RED+"先に左クリックで地点Aを設定してください。");return;}if(!a.getWorld().equals(l.getWorld())){p.sendMessage(ChatColor.RED+"同じワールド内で指定してください。");return;}plugin.getEventEditorManager().setArea(id,a,l);areaEvent.remove(p.getUniqueId());pos1.remove(p.getUniqueId());p.sendMessage(ChatColor.GREEN+"イベントエリアを保存しました: "+fmt(a)+" → "+fmt(l));plugin.getEventEditorManager().open(p);}
    }
    private String fmt(Location l){return l.getBlockX()+","+l.getBlockY()+","+l.getBlockZ();}
    private void drawDebug(){for(UUID u:new HashSet<>(debug)){Player p=Bukkit.getPlayer(u);if(p==null){debug.remove(u);continue;}for(EventEditorManager.EventDef d:plugin.getEventEditorManager().all()){if(!d.hasArea()||!p.getWorld().getName().equals(d.world))continue;int minX=Math.min(d.x1,d.x2),maxX=Math.max(d.x1,d.x2),minY=Math.min(d.y1,d.y2),maxY=Math.max(d.y1,d.y2),minZ=Math.min(d.z1,d.z2),maxZ=Math.max(d.z1,d.z2);World w=p.getWorld();for(int x=minX;x<=maxX;x+=Math.max(1,(maxX-minX)/8+1)){p.spawnParticle(Particle.END_ROD,new Location(w,x+.5,minY+.2,minZ+.5),1,0,0,0,0);p.spawnParticle(Particle.END_ROD,new Location(w,x+.5,minY+.2,maxZ+.5),1,0,0,0,0);}for(int z=minZ;z<=maxZ;z+=Math.max(1,(maxZ-minZ)/8+1)){p.spawnParticle(Particle.END_ROD,new Location(w,minX+.5,minY+.2,z+.5),1,0,0,0,0);p.spawnParticle(Particle.END_ROD,new Location(w,maxX+.5,minY+.2,z+.5),1,0,0,0,0);}}}}
}
