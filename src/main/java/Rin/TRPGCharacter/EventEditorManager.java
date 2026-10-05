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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rebuild Fix10 event engine/editor.
 * Event = Trigger -> Conditions (reserved/extensible) -> ordered Actions.
 * Initial triggers: MANUAL / AREA_ENTER. Actions are executed as a timeline.
 */
public class EventEditorManager implements Listener {
    public enum Trigger { MANUAL, AREA_ENTER }
    public enum Repeat { ONCE, PLAYER_ONCE, REPEAT }
    public enum ActionType { MESSAGE, TITLE, SOUND, WAIT, TELEPORT_HERE, SET_TIME, WEATHER }
    public enum ConditionType { HAS_ITEM, HP_BELOW }

    public static final class EventDef {
        String id, name;
        Trigger trigger = Trigger.MANUAL;
        Repeat repeat = Repeat.REPEAT;
        String world;
        int x1,y1,z1,x2,y2,z2;
        final List<ActionDef> actions = new ArrayList<>();
        final List<ConditionDef> conditions = new ArrayList<>();
        final Set<UUID> firedPlayers = new HashSet<>();
        boolean firedGlobal;
        boolean hasArea(){ return world != null; }
        boolean contains(Location l){
            if(!hasArea() || l.getWorld()==null || !l.getWorld().getName().equals(world)) return false;
            int minX=Math.min(x1,x2), maxX=Math.max(x1,x2), minY=Math.min(y1,y2), maxY=Math.max(y1,y2), minZ=Math.min(z1,z2), maxZ=Math.max(z1,z2);
            return l.getBlockX()>=minX&&l.getBlockX()<=maxX&&l.getBlockY()>=minY&&l.getBlockY()<=maxY&&l.getBlockZ()>=minZ&&l.getBlockZ()<=maxZ;
        }
    }
    public static final class ConditionDef { ConditionType type; String value; ConditionDef(ConditionType t,String v){type=t;value=v;} }
    public static final class ActionDef {
        ActionType type; String value;
        ActionDef(ActionType type,String value){this.type=type;this.value=value;}
    }

    private final Plugin plugin;
    private final File file;
    private final Map<String,EventDef> events = new LinkedHashMap<>();
    private final Map<UUID,String> editing = new HashMap<>();
    private final Map<UUID,String> chatRename = new ConcurrentHashMap<>();
    private final Map<UUID,String> chatAction = new ConcurrentHashMap<>();
    private final Map<UUID,Set<String>> inside = new HashMap<>();
    private final NamespacedKey eventIdKey;
    private static final Component LIST_TITLE=Component.text("EVENT EDITOR",NamedTextColor.DARK_PURPLE);
    private static final Component EDIT_TITLE=Component.text("EVENT SETTINGS",NamedTextColor.DARK_AQUA);

    public EventEditorManager(Plugin plugin){
        this.plugin=plugin; this.file=new File(plugin.getDataFolder(),"events.yml"); this.eventIdKey=new NamespacedKey(plugin,"event_editor_id"); load();
    }

    public void open(Player p){
        Inventory inv=Bukkit.createInventory(null,54,LIST_TITLE); int slot=0;
        for(EventDef d:events.values()){
            if(slot>=45)break;
            ItemStack i=item(d.trigger==Trigger.AREA_ENTER?Material.LIME_DYE:Material.REDSTONE_TORCH,d.name,
                    "ID: "+d.id,"Trigger: "+d.trigger,"Repeat: "+d.repeat,"Conditions: "+d.conditions.size(),"Actions: "+d.actions.size(), d.hasArea()?"Area: 設定済み":"Area: 未設定");
            ItemMeta m=i.getItemMeta();m.getPersistentDataContainer().set(eventIdKey,PersistentDataType.STRING,d.id);i.setItemMeta(m);inv.setItem(slot++,i);
        }
        inv.setItem(45,item(Material.EMERALD,"新規イベント","クリックで作成"));
        inv.setItem(49,item(Material.BLAZE_ROD,"EDITOR WANDへ戻る"));
        inv.setItem(53,item(Material.KNOWLEDGE_BOOK,"イベント仕様","Trigger → Condition → Action","Actionは上から順にタイムライン実行"));
        p.openInventory(inv);
    }

    private void openEdit(Player p,EventDef d){
        editing.put(p.getUniqueId(),d.id);
        Inventory inv=Bukkit.createInventory(null,45,EDIT_TITLE);
        inv.setItem(10,item(Material.NAME_TAG,"名前: "+d.name,"クリック: チャットで変更"));
        inv.setItem(11,item(Material.TRIPWIRE_HOOK,"Trigger: "+d.trigger,"クリック: MANUAL / AREA_ENTER"));
        inv.setItem(12,item(Material.REPEATER,"Repeat: "+d.repeat,"ONCE / PLAYER_ONCE / REPEAT"));
        inv.setItem(13,item(Material.GOLDEN_AXE,"エリア設定",d.hasArea()?"設定済み":"未設定","クリック後、WANDで左クリック地点A / 右クリック地点B"));
        inv.setItem(14,item(Material.COMPARATOR,"Condition追加","左: 手に持つアイテムを要求","右: HP 5以下を要求","現在: "+d.conditions.size()+"件"));
        inv.setItem(15,item(Material.REDSTONE,"Action追加","左: MESSAGE","右: WAIT","Shift左: TITLE","Shift右: SOUND"));
        inv.setItem(16,item(Material.ENDER_PEARL,"便利Action追加","左: 現在地点TP","右: 時刻を現在時刻へ","Shift左: 天候=晴れ"));
        int s=18; for(int n=0;n<d.actions.size()&&s<36;n++,s++){
            ActionDef a=d.actions.get(n);inv.setItem(s,item(materialFor(a.type),(n+1)+". "+a.type,"値: "+a.value,"左クリック: 値をチャット編集","右クリック: 削除"));
        }
        inv.setItem(36,item(Material.LIME_CONCRETE,"今すぐテスト実行"));
        inv.setItem(40,item(Material.ARROW,"一覧へ戻る"));
        inv.setItem(44,item(Material.TNT,"イベント削除","Shift+クリックで削除"));
        p.openInventory(inv);
    }

    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p)||e.getCurrentItem()==null)return;
        if(e.getView().title().equals(LIST_TITLE)){
            e.setCancelled(true);
            if(e.getRawSlot()==45){ EventDef d=new EventDef();d.id="event_"+(events.size()+1)+"_"+Integer.toHexString((int)(System.currentTimeMillis()&0xffff));d.name="新規イベント";d.actions.add(new ActionDef(ActionType.MESSAGE,"イベントが発生した。"));events.put(d.id,d);save();openEdit(p,d);return; }
            if(e.getRawSlot()==49){plugin.getKpToolManager().openEditorMenu(p);return;}
            String id=e.getCurrentItem().getItemMeta().getPersistentDataContainer().get(eventIdKey,PersistentDataType.STRING); if(id!=null&&events.containsKey(id))openEdit(p,events.get(id)); return;
        }
        if(!e.getView().title().equals(EDIT_TITLE))return;
        e.setCancelled(true); EventDef d=current(p); if(d==null){open(p);return;} int slot=e.getRawSlot();
        if(slot==10){chatRename.put(p.getUniqueId(),d.id);p.closeInventory();p.sendMessage(ChatColor.AQUA+"イベント名をチャット入力してください。 cancel で中止。");}
        else if(slot==11){d.trigger=d.trigger==Trigger.MANUAL?Trigger.AREA_ENTER:Trigger.MANUAL;save();openEdit(p,d);}
        else if(slot==12){d.repeat=Repeat.values()[(d.repeat.ordinal()+1)%Repeat.values().length];save();openEdit(p,d);}
        else if(slot==13){d.trigger=Trigger.AREA_ENTER;save();p.closeInventory();plugin.getEditorWandManager().beginAreaSelection(p,d.id);}
        else if(slot==14){if(e.isRightClick())d.conditions.add(new ConditionDef(ConditionType.HP_BELOW,"5"));else {Material held=p.getInventory().getItemInMainHand().getType();if(held==Material.AIR){p.sendMessage(ChatColor.RED+"要求したいアイテムを手に持ってください。");return;}d.conditions.add(new ConditionDef(ConditionType.HAS_ITEM,held.name()));}save();openEdit(p,d);}
        else if(slot==15){
            if(e.isShiftClick()&&e.isRightClick()) d.actions.add(new ActionDef(ActionType.SOUND,"BLOCK_AMETHYST_BLOCK_CHIME"));
            else if(e.isShiftClick()) d.actions.add(new ActionDef(ActionType.TITLE,"――何かが起きた。"));
            else if(e.isRightClick()) d.actions.add(new ActionDef(ActionType.WAIT,"40"));
            else d.actions.add(new ActionDef(ActionType.MESSAGE,"何かが起きた。")); save();openEdit(p,d);
        } else if(slot==16){
            if(e.isShiftClick())d.actions.add(new ActionDef(ActionType.WEATHER,"CLEAR"));
            else if(e.isRightClick())d.actions.add(new ActionDef(ActionType.SET_TIME,String.valueOf(p.getWorld().getTime())));
            else {Location l=p.getLocation();d.actions.add(new ActionDef(ActionType.TELEPORT_HERE,l.getWorld().getName()+","+l.getX()+","+l.getY()+","+l.getZ()+","+l.getYaw()+","+l.getPitch()));} save();openEdit(p,d);
        } else if(slot>=18&&slot<36){int idx=slot-18;if(idx<d.actions.size()){if(e.isRightClick()){d.actions.remove(idx);save();openEdit(p,d);}else{chatAction.put(p.getUniqueId(),d.id+":"+idx);p.closeInventory();p.sendMessage(ChatColor.AQUA+"Action値を入力してください。WAITはtick、SoundはSound名。 cancelで中止。");}}}
        else if(slot==36){run(d,p,true);}
        else if(slot==40){open(p);}
        else if(slot==44&&e.isShiftClick()){events.remove(d.id);editing.remove(p.getUniqueId());save();open(p);}
    }

    @SuppressWarnings("deprecation") @EventHandler public void chat(AsyncPlayerChatEvent e){
        String act=chatAction.remove(e.getPlayer().getUniqueId()); String id=chatRename.remove(e.getPlayer().getUniqueId()); if(act==null&&id==null)return;e.setCancelled(true);String msg=e.getMessage();
        Bukkit.getScheduler().runTask(plugin,()->{if(act!=null){String[] q=act.split(":");EventDef d=events.get(q[0]);if(d==null)return;int idx=Integer.parseInt(q[1]);if(idx<d.actions.size()&&!msg.equalsIgnoreCase("cancel")){d.actions.get(idx).value=msg;save();}openEdit(e.getPlayer(),d);return;}EventDef d=events.get(id);if(d==null)return;if(!msg.equalsIgnoreCase("cancel")){d.name=msg;save();}openEdit(e.getPlayer(),d);});
    }

    @EventHandler public void move(PlayerMoveEvent e){
        if(e.getTo()==null||e.getFrom().getBlock().equals(e.getTo().getBlock()))return;
        Set<String> now=inside.computeIfAbsent(e.getPlayer().getUniqueId(),k->new HashSet<>());
        for(EventDef d:events.values()) if(d.trigger==Trigger.AREA_ENTER&&d.hasArea()){
            boolean was=now.contains(d.id), is=d.contains(e.getTo()); if(is&&!was){now.add(d.id);run(d,e.getPlayer(),false);} else if(!is&&was)now.remove(d.id);
        }
    }

    public void setArea(String id,Location a,Location b){EventDef d=events.get(id);if(d==null||a.getWorld()==null||b.getWorld()==null||!a.getWorld().equals(b.getWorld()))return;d.world=a.getWorld().getName();d.x1=a.getBlockX();d.y1=a.getBlockY();d.z1=a.getBlockZ();d.x2=b.getBlockX();d.y2=b.getBlockY();d.z2=b.getBlockZ();d.trigger=Trigger.AREA_ENTER;save();}
    public EventDef get(String id){return events.get(id);} public Collection<EventDef> all(){return Collections.unmodifiableCollection(events.values());}
    public void runById(String id,Player p){EventDef d=events.get(id);if(d!=null)run(d,p,true);}
    private void run(EventDef d,Player p,boolean force){
        if(!force){if(d.repeat==Repeat.ONCE&&d.firedGlobal)return;if(d.repeat==Repeat.PLAYER_ONCE&&d.firedPlayers.contains(p.getUniqueId()))return;}
        if(!conditionsPass(d,p)){p.sendMessage(ChatColor.GRAY+"[Event] 条件を満たしていません。");return;}
        if(d.repeat==Repeat.ONCE)d.firedGlobal=true;if(d.repeat==Repeat.PLAYER_ONCE)d.firedPlayers.add(p.getUniqueId());save(); execute(d,p,0,0);
    }
    private void execute(EventDef d,Player p,int index,long delay){
        if(index>=d.actions.size())return;ActionDef a=d.actions.get(index);
        if(a.type==ActionType.WAIT){long ticks=parseLong(a.value,20);Bukkit.getScheduler().runTaskLater(plugin,()->execute(d,p,index+1,0),Math.max(1,ticks));return;}
        Bukkit.getScheduler().runTaskLater(plugin,()->{apply(a,p);execute(d,p,index+1,0);},delay);
    }
    private boolean conditionsPass(EventDef d,Player p){for(ConditionDef c:d.conditions){try{if(c.type==ConditionType.HAS_ITEM){Material m=Material.valueOf(c.value);boolean found=Arrays.stream(p.getInventory().getContents()).filter(Objects::nonNull).anyMatch(i->i.getType()==m);if(!found)return false;}else if(c.type==ConditionType.HP_BELOW){if(plugin.getCharacterManagerInternal().getCurrentHp(p)>Integer.parseInt(c.value))return false;}}catch(Exception ex){return false;}}return true;}
    private void apply(ActionDef a,Player p){try{switch(a.type){
        case MESSAGE -> p.sendMessage(ChatColor.translateAlternateColorCodes('&',a.value));
        case TITLE -> p.showTitle(org.bukkit.Title.title(Component.text(a.value),Component.empty()));
        case SOUND -> p.playSound(p.getLocation(),Sound.valueOf(a.value),1f,1f);
        case TELEPORT_HERE -> {String[] s=a.value.split(",");World w=Bukkit.getWorld(s[0]);if(w!=null)p.teleport(new Location(w,Double.parseDouble(s[1]),Double.parseDouble(s[2]),Double.parseDouble(s[3]),Float.parseFloat(s[4]),Float.parseFloat(s[5])));}
        case SET_TIME -> p.getWorld().setTime(parseLong(a.value,6000));
        case WEATHER -> {boolean rain=!a.value.equalsIgnoreCase("CLEAR");p.getWorld().setStorm(rain);p.getWorld().setThundering(a.value.equalsIgnoreCase("THUNDER"));}
        default -> {}
    }}catch(Exception ex){plugin.getLogger().warning("Event action failed: "+a.type+" / "+ex.getMessage());}}

    private EventDef current(Player p){String id=editing.get(p.getUniqueId());return id==null?null:events.get(id);} private long parseLong(String s,long d){try{return Long.parseLong(s);}catch(Exception e){return d;}}
    private Material materialFor(ActionType t){return switch(t){case MESSAGE->Material.PAPER;case TITLE->Material.OAK_SIGN;case SOUND->Material.NOTE_BLOCK;case WAIT->Material.CLOCK;case TELEPORT_HERE->Material.ENDER_PEARL;case SET_TIME->Material.SUNFLOWER;case WEATHER->Material.WATER_BUCKET;};}
    private ItemStack item(Material m,String name,String... lore){ItemStack i=new ItemStack(m);ItemMeta meta=i.getItemMeta();meta.displayName(Component.text(name,NamedTextColor.AQUA));if(lore.length>0)meta.lore(Arrays.stream(lore).map(x->Component.text(x,NamedTextColor.GRAY)).toList());i.setItemMeta(meta);return i;}

    private void load(){events.clear();if(!file.exists())return;YamlConfiguration y=YamlConfiguration.loadConfiguration(file);ConfigurationSection root=y.getConfigurationSection("events");if(root==null)return;for(String id:root.getKeys(false)){ConfigurationSection s=root.getConfigurationSection(id);if(s==null)continue;EventDef d=new EventDef();d.id=id;d.name=s.getString("name",id);try{d.trigger=Trigger.valueOf(s.getString("trigger","MANUAL"));}catch(Exception ignored){}try{d.repeat=Repeat.valueOf(s.getString("repeat","REPEAT"));}catch(Exception ignored){}d.world=s.getString("area.world");d.x1=s.getInt("area.x1");d.y1=s.getInt("area.y1");d.z1=s.getInt("area.z1");d.x2=s.getInt("area.x2");d.y2=s.getInt("area.y2");d.z2=s.getInt("area.z2");d.firedGlobal=s.getBoolean("state.fired-global",false);for(String u:s.getStringList("state.fired-players"))try{d.firedPlayers.add(UUID.fromString(u));}catch(Exception ignored){}for(Map<?,?> map:s.getMapList("conditions")){try{d.conditions.add(new ConditionDef(ConditionType.valueOf(String.valueOf(map.get("type"))),String.valueOf(map.get("value"))));}catch(Exception ignored){}}for(Map<?,?> map:s.getMapList("actions")){try{d.actions.add(new ActionDef(ActionType.valueOf(String.valueOf(map.get("type"))),String.valueOf(map.get("value"))));}catch(Exception ignored){}}events.put(id,d);}}
    public void save(){YamlConfiguration y=new YamlConfiguration();for(EventDef d:events.values()){String b="events."+d.id+".";y.set(b+"name",d.name);y.set(b+"trigger",d.trigger.name());y.set(b+"repeat",d.repeat.name());y.set(b+"area.world",d.world);y.set(b+"area.x1",d.x1);y.set(b+"area.y1",d.y1);y.set(b+"area.z1",d.z1);y.set(b+"area.x2",d.x2);y.set(b+"area.y2",d.y2);y.set(b+"area.z2",d.z2);y.set(b+"state.fired-global",d.firedGlobal);y.set(b+"state.fired-players",d.firedPlayers.stream().map(UUID::toString).toList());List<Map<String,Object>> conds=new ArrayList<>();for(ConditionDef c:d.conditions){Map<String,Object> m=new LinkedHashMap<>();m.put("type",c.type.name());m.put("value",c.value);conds.add(m);}y.set(b+"conditions",conds);List<Map<String,Object>> acts=new ArrayList<>();for(ActionDef a:d.actions){Map<String,Object> m=new LinkedHashMap<>();m.put("type",a.type.name());m.put("value",a.value);acts.add(m);}y.set(b+"actions",acts);}try{y.save(file);}catch(IOException e){plugin.getLogger().severe("events.yml save failed: "+e.getMessage());}}
}
