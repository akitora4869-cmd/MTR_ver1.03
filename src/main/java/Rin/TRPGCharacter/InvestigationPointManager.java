package Rin.TRPGCharacter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Fix11: EDITOR WANDで設置できる調査ポイント。技能判定と4段階結果からEventへ接続する。 */
public class InvestigationPointManager implements Listener {
    public enum Visibility { PUBLIC, SECRET, HIDDEN }
    static final class Point { String id,name="調査ポイント",world,skill="spot_hidden",successEvent="",failureEvent="",criticalEvent="",fumbleEvent=""; int x,y,z; Visibility visibility=Visibility.PUBLIC; boolean playerOnce=true; Set<UUID> used=new HashSet<>(); }
    private final Plugin plugin; private final File file; private final Map<String,Point> points=new LinkedHashMap<>();
    private final Map<UUID,String> editing=new HashMap<>(), awaiting=new ConcurrentHashMap<>();
    private static final Component TITLE=Component.text("INVESTIGATION POINT",NamedTextColor.DARK_AQUA);
    public InvestigationPointManager(Plugin plugin){this.plugin=plugin;file=new File(plugin.getDataFolder(),"investigation-points.yml");load();}
    public Collection<Location> locations(){List<Location> out=new ArrayList<>();for(Point q:points.values()){World w=Bukkit.getWorld(q.world);if(w!=null)out.add(new Location(w,q.x+.5,q.y+.5,q.z+.5));}return out;}
    public void createAt(Player p,Location l){Point q=find(l);if(q==null){q=new Point();q.id="point_"+Integer.toHexString((int)(System.currentTimeMillis()&0xffffff));q.world=l.getWorld().getName();q.x=l.getBlockX();q.y=l.getBlockY();q.z=l.getBlockZ();points.put(q.id,q);save();}open(p,q);}
    private Point find(Location l){if(l==null||l.getWorld()==null)return null;for(Point q:points.values())if(q.world.equals(l.getWorld().getName())&&q.x==l.getBlockX()&&q.y==l.getBlockY()&&q.z==l.getBlockZ())return q;return null;}
    private void open(Player p,Point q){editing.put(p.getUniqueId(),q.id);Inventory inv=Bukkit.createInventory(null,36,TITLE);inv.setItem(10,item(Material.NAME_TAG,"名前: "+q.name,"クリック→チャット入力"));inv.setItem(11,item(Material.SPYGLASS,"技能: "+q.skill,"技能IDをチャット入力"));inv.setItem(12,item(Material.ENDER_EYE,"公開: "+q.visibility,"PUBLIC / SECRET / HIDDEN"));inv.setItem(13,item(Material.REPEATER,"探索者ごと1回: "+q.playerOnce,"クリック切替"));inv.setItem(19,item(Material.EMERALD,"成功 Event: "+blank(q.successEvent)));inv.setItem(20,item(Material.DIAMOND,"クリティカル Event: "+blank(q.criticalEvent)));inv.setItem(21,item(Material.COAL,"失敗 Event: "+blank(q.failureEvent)));inv.setItem(22,item(Material.TNT,"ファンブル Event: "+blank(q.fumbleEvent)));inv.setItem(27,item(Material.LIME_CONCRETE,"この場でテスト"));inv.setItem(31,item(Material.ARROW,"EDITORへ戻る"));inv.setItem(35,item(Material.BARRIER,"削除","Shift+クリック"));p.openInventory(inv);}
    private String blank(String s){return s==null||s.isBlank()?"なし":s;}
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p)||!e.getView().title().equals(TITLE)||e.getCurrentItem()==null)return;e.setCancelled(true);Point q=points.get(editing.get(p.getUniqueId()));if(q==null)return;int s=e.getRawSlot();if(s==10)ask(p,"name");else if(s==11)ask(p,"skill");else if(s==12){q.visibility=Visibility.values()[(q.visibility.ordinal()+1)%3];save();open(p,q);}else if(s==13){q.playerOnce=!q.playerOnce;save();open(p,q);}else if(s>=19&&s<=22){String k=s==19?"success":s==20?"critical":s==21?"failure":"fumble";ask(p,k);}else if(s==27)run(p,q,true);else if(s==31)plugin.getKpToolManager().openEditorMenu(p);else if(s==35&&e.isShiftClick()){points.remove(q.id);save();plugin.getKpToolManager().openEditorMenu(p);}}
    private void ask(Player p,String key){awaiting.put(p.getUniqueId(),key);p.closeInventory();p.sendMessage(ChatColor.AQUA+"値をチャット入力してください。EventはID、noneで解除、cancelで中止。");}
    @SuppressWarnings("deprecation") @EventHandler public void chat(AsyncPlayerChatEvent e){String k=awaiting.remove(e.getPlayer().getUniqueId());if(k==null)return;e.setCancelled(true);String msg=e.getMessage();Bukkit.getScheduler().runTask(plugin,()->{Point q=points.get(editing.get(e.getPlayer().getUniqueId()));if(q==null)return;if(!msg.equalsIgnoreCase("cancel")){String v=msg.equalsIgnoreCase("none")?"":msg;switch(k){case"name"->q.name=v;case"skill"->q.skill=v;case"success"->q.successEvent=v;case"failure"->q.failureEvent=v;case"critical"->q.criticalEvent=v;case"fumble"->q.fumbleEvent=v;}save();}open(e.getPlayer(),q);});}
    @EventHandler public void interact(PlayerInteractEvent e){if(e.getClickedBlock()==null||!e.getAction().isRightClick())return;Point q=find(e.getClickedBlock().getLocation());if(q==null)return;if(plugin.getEditorWandManager().isWand(e.getItem())){e.setCancelled(true);open(e.getPlayer(),q);return;}e.setCancelled(true);run(e.getPlayer(),q,false);}
    private void run(Player p,Point q,boolean force){if(!force&&q.playerOnce&&q.used.contains(p.getUniqueId())){p.sendMessage(ChatColor.GRAY+"ここは既に調査済みだ。");return;}int target=plugin.getSkillManager().getSkillValue(p,q.skill);int roll=1+new Random().nextInt(100);CheckResult r=CheckResult.evaluate(roll,target);if(q.playerOnce){q.used.add(p.getUniqueId());save();}String label=Optional.ofNullable(plugin.getSkillManager().getSkill(q.skill)).map(SkillDefinition::getName).orElse(q.skill);if(q.visibility==Visibility.PUBLIC){Bukkit.broadcastMessage(ChatColor.GOLD+"[調査] "+p.getName()+" - "+label+" "+roll+"/"+target+" → "+r.label());}else {if(q.visibility==Visibility.SECRET)p.sendMessage(ChatColor.DARK_PURPLE+"🔒 SECRET ROLL 〈？？？〉");String m=ChatColor.DARK_PURPLE+"[SECRET調査] "+p.getName()+" - "+label+" "+roll+"/"+target+" → "+r.label();for(Player kp:Bukkit.getOnlinePlayers())if(kp.isOp()||kp.hasPermission("trpg.admin")||plugin.getKeeperManager().isKeeper(kp))kp.sendMessage(m);}String id=r==CheckResult.CRITICAL?q.criticalEvent:r==CheckResult.FUMBLE?q.fumbleEvent:r.isSuccess()?q.successEvent:q.failureEvent;if(id!=null&&!id.isBlank())plugin.getEventEditorManager().runById(id,p);}
    private ItemStack item(Material m,String n,String...l){ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();meta.displayName(Component.text(n,NamedTextColor.AQUA));if(l.length>0)meta.lore(Arrays.stream(l).map(x->Component.text(x,NamedTextColor.GRAY)).toList());i.setItemMeta(meta);return i;}
    private void load(){if(!file.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(file);ConfigurationSection root=y.getConfigurationSection("points");if(root==null)return;for(String id:root.getKeys(false)){ConfigurationSection s=root.getConfigurationSection(id);Point q=new Point();q.id=id;q.name=s.getString("name",q.name);q.world=s.getString("world","");q.x=s.getInt("x");q.y=s.getInt("y");q.z=s.getInt("z");q.skill=s.getString("skill",q.skill);try{q.visibility=Visibility.valueOf(s.getString("visibility","PUBLIC"));}catch(Exception ignored){}q.playerOnce=s.getBoolean("player-once",true);q.successEvent=s.getString("events.success","");q.failureEvent=s.getString("events.failure","");q.criticalEvent=s.getString("events.critical","");q.fumbleEvent=s.getString("events.fumble","");for(String u:s.getStringList("used"))try{q.used.add(UUID.fromString(u));}catch(Exception ignored){}points.put(id,q);}}
    public void save(){YamlConfiguration y=new YamlConfiguration();for(Point q:points.values()){String b="points."+q.id+".";y.set(b+"name",q.name);y.set(b+"world",q.world);y.set(b+"x",q.x);y.set(b+"y",q.y);y.set(b+"z",q.z);y.set(b+"skill",q.skill);y.set(b+"visibility",q.visibility.name());y.set(b+"player-once",q.playerOnce);y.set(b+"events.success",q.successEvent);y.set(b+"events.failure",q.failureEvent);y.set(b+"events.critical",q.criticalEvent);y.set(b+"events.fumble",q.fumbleEvent);y.set(b+"used",q.used.stream().map(UUID::toString).toList());}try{y.save(file);}catch(IOException ex){plugin.getLogger().warning("investigation-points.yml save failed: "+ex.getMessage());}}
}
