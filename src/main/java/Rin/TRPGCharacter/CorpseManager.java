package Rin.TRPGCharacter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Persistent corpse + blood-stain system for investigators and human-like entities. */
public class CorpseManager implements Listener {
    public enum CorpseState {
        CLEAN("比較的綺麗な遺体", "外見から人物を判別できる。", 1),
        DAMAGED("激しく損傷した遺体", "損傷が激しい。詳しい確認には調査が必要そうだ。", 2),
        HARD_TO_IDENTIFY("判別困難な遺体", "外見だけで人物を特定するのは難しい。", 3),
        UNRECOGNIZABLE("原形を留めない遺体", "外見からの身元判別はほぼ不可能だ。", 4);
        final String label, description; final int blood;
        CorpseState(String l, String d, int b){label=l;description=d;blood=b;}
        public static CorpseState fromHp(int hp){
            if(hp >= -2) return CLEAN;
            if(hp >= -5) return DAMAGED;
            if(hp >= -9) return HARD_TO_IDENTIFY;
            return UNRECOGNIZABLE;
        }
    }

    private final Plugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final NamespacedKey corpseKey;
    private final NamespacedKey bloodKey;
    private final Map<UUID, SpawnedCorpse> spawned = new HashMap<>();
    private final Map<UUID, Double> pendingHumanHp = new HashMap<>();
    private static final long BLOOD_LIFETIME_MS = 15L * 60L * 1000L;

    public CorpseManager(Plugin plugin){
        this.plugin=plugin;
        this.file=new File(plugin.getDataFolder(), "corpses.yml");
        this.data=YamlConfiguration.loadConfiguration(file);
        this.corpseKey=new NamespacedKey(plugin,"corpse_id");
        this.bloodKey=new NamespacedKey(plugin,"blood_id");
        plugin.getServer().getScheduler().runTaskLater(plugin, this::respawnAll, 20L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanupExpiredBlood, 20L*30L, 20L*30L);
    }

    public void createPlayerCorpse(Player player, int deathHp){
        createCorpse(player.getName(), player.getUniqueId(), player.getLocation(), deathHp, true, null);
    }

    public void createMythosCorpse(String name, String mythosId, Location location){
        createCorpse(name, null, location, 0, false, mythosId);
    }

    private void createCorpse(String name, UUID owner, Location loc, int deathHp, boolean player, String mythosId){
        UUID id=UUID.randomUUID(); CorpseState state=CorpseState.fromHp(deathHp); long now=System.currentTimeMillis();
        String p="corpses."+id;
        data.set(p+".name",name); data.set(p+".owner",owner==null?null:owner.toString()); data.set(p+".world",loc.getWorld().getName());
        data.set(p+".x",loc.getX()); data.set(p+".y",loc.getY()); data.set(p+".z",loc.getZ()); data.set(p+".yaw",loc.getYaw());
        data.set(p+".death-hp",deathHp); data.set(p+".state",state.name()); data.set(p+".death-time",now); data.set(p+".player",player); data.set(p+".mythos-id",mythosId);
        data.set(p+".blood-expires",now+BLOOD_LIFETIME_MS); save(); spawn(id);
    }

    @EventHandler(ignoreCancelled=true)
    public void onHumanDamage(EntityDamageEvent e){
        if(e.getEntity() instanceof Player) return;
        if(!isHumanLike(e.getEntity())) return;
        double after=e.getEntity() instanceof LivingEntity le ? le.getHealth()-e.getFinalDamage() : 1;
        pendingHumanHp.put(e.getEntity().getUniqueId(), after);
    }

    @EventHandler
    public void onHumanDeath(EntityDeathEvent e){
        if(e.getEntity() instanceof Player || !isHumanLike(e.getEntity())) return;
        LivingEntity le=e.getEntity(); Double recorded=pendingHumanHp.remove(le.getUniqueId()); double after=recorded==null ? 0 : recorded;
        // EntityDeathEvent fires at zero; the last recorded post-hit HP gives an overkill approximation.
        int deathHp=(int)Math.floor(Math.min(0, after));
        String name=le.customName()!=null ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(le.customName()) : le.getType().name();
        createCorpse(name, le.getUniqueId(), le.getLocation(), deathHp, false, null);
    }

    private boolean isHumanLike(Entity e){
        return e.getType()==EntityType.VILLAGER || e.getType()==EntityType.WANDERING_TRADER;
    }

    private void spawn(UUID id){
        String p="corpses."+id; World w=Bukkit.getWorld(data.getString(p+".world","")); if(w==null)return;
        Location l=new Location(w,data.getDouble(p+".x"),data.getDouble(p+".y")+0.08,data.getDouble(p+".z"), (float)data.getDouble(p+".yaw"),0);
        CorpseState state=CorpseState.valueOf(data.getString(p+".state",CorpseState.CLEAN.name()));
        ItemStack icon=corpseIcon(p,state);
        ItemDisplay body=w.spawn(l,ItemDisplay.class,d->{d.setItemStack(icon);d.setBillboard(Display.Billboard.FIXED);d.getPersistentDataContainer().set(corpseKey,PersistentDataType.STRING,id.toString());
            float scale=state==CorpseState.CLEAN?1.25f:state==CorpseState.DAMAGED?1.1f:0.9f;
            d.setTransformation(new Transformation(new Vector3f(0,0,0),new AxisAngle4f((float)Math.toRadians(90),1,0,0),new Vector3f(scale,scale,scale),new AxisAngle4f()));});
        Interaction interaction=w.spawn(l.clone().add(0,0.35,0),Interaction.class,i->{i.setInteractionWidth(1.3f);i.setInteractionHeight(0.6f);i.getPersistentDataContainer().set(corpseKey,PersistentDataType.STRING,id.toString());});
        List<Entity> blood=new ArrayList<>(); long expiry=data.getLong(p+".blood-expires",0);
        if(System.currentTimeMillis()<expiry){
            Random r=new Random(id.getMostSignificantBits());
            for(int n=0;n<state.blood;n++){
                Location bl=l.clone().add((r.nextDouble()-.5)*1.4, -0.06, (r.nextDouble()-.5)*1.4);
                ItemDisplay stain=w.spawn(bl,ItemDisplay.class,d->{d.setItemStack(new ItemStack(Material.RED_DYE));d.setBillboard(Display.Billboard.FIXED);d.getPersistentDataContainer().set(bloodKey,PersistentDataType.STRING,id.toString());d.setTransformation(new Transformation(new Vector3f(),new AxisAngle4f((float)Math.toRadians(90),1,0,0),new Vector3f(.55f,.08f,.55f),new AxisAngle4f()));});
                blood.add(stain);
            }
        }
        spawned.put(id,new SpawnedCorpse(body,interaction,blood));
    }

    private ItemStack corpseIcon(String p, CorpseState state){
        if("deep_one".equalsIgnoreCase(data.getString(p+".mythos-id",""))){
            ItemStack body=new ItemStack(Material.PAPER); var meta=body.getItemMeta(); meta.setCustomModelData(DeepOneVisualManager.MODEL_DATA); body.setItemMeta(meta); return body;
        }
        if(data.getBoolean(p+".player",false) && state!=CorpseState.UNRECOGNIZABLE){
            ItemStack head=new ItemStack(Material.PLAYER_HEAD); SkullMeta sm=(SkullMeta)head.getItemMeta();
            String raw=data.getString(p+".owner"); if(raw!=null)try{sm.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(raw)));}catch(Exception ignored){}
            head.setItemMeta(sm); return head;
        }
        return new ItemStack(state==CorpseState.UNRECOGNIZABLE?Material.NETHERRACK:Material.SKELETON_SKULL);
    }

    @EventHandler
    public void onInspect(PlayerInteractEntityEvent e){
        String raw=e.getRightClicked().getPersistentDataContainer().get(corpseKey,PersistentDataType.STRING); if(raw==null)return;
        e.setCancelled(true); UUID id; try{id=UUID.fromString(raw);}catch(Exception ex){return;} String p="corpses."+id;
        CorpseState state=CorpseState.valueOf(data.getString(p+".state",CorpseState.CLEAN.name())); int hp=data.getInt(p+".death-hp");
        String mythosId=data.getString(p+".mythos-id","");
        if("deep_one".equalsIgnoreCase(mythosId)){
            e.getPlayer().sendMessage(Component.text("【深きものの死骸】",NamedTextColor.DARK_AQUA));
            e.getPlayer().sendMessage(Component.text("魚類と人型生物の特徴が入り混じった、不気味な死骸だ。",NamedTextColor.GRAY));
            if(!data.getBoolean(p+".deep-one-spear-recovered",false) && plugin.getDeepOneSpearManager()!=null){
                e.getPlayer().getInventory().addItem(plugin.getDeepOneSpearManager().createSpear());
                data.set(p+".deep-one-spear-recovered",true); save();
                e.getPlayer().sendMessage(Component.text("死骸の傍らから、荒削りな石槍を回収した。",NamedTextColor.AQUA));
            } else if(data.getBoolean(p+".deep-one-spear-recovered",false)){
                e.getPlayer().sendMessage(Component.text("石槍はすでに回収されている。",NamedTextColor.DARK_GRAY));
            }
        } else {
            e.getPlayer().sendMessage(Component.text("【遺体】 ",NamedTextColor.DARK_RED).append(Component.text(state.label,NamedTextColor.RED)));
            e.getPlayer().sendMessage(Component.text(state.description,NamedTextColor.GRAY));
        }
        if(state==CorpseState.CLEAN || state==CorpseState.DAMAGED) e.getPlayer().sendMessage(Component.text("人物: "+data.getString(p+".name","不明"),NamedTextColor.YELLOW));
        if(canKp(e.getPlayer())) e.getPlayer().sendMessage(Component.text("[KP] 死亡時HP: "+hp+" / ID: "+id,NamedTextColor.DARK_GRAY));
    }

    public void openKpGui(Player kp){
        org.bukkit.inventory.Inventory inv=Bukkit.createInventory(null,54,Component.text("遺体・血痕管理",NamedTextColor.DARK_RED)); int slot=0;
        ConfigurationSection sec=data.getConfigurationSection("corpses"); if(sec!=null)for(String key:sec.getKeys(false)){if(slot>=45)break;String p="corpses."+key;CorpseState st=CorpseState.valueOf(data.getString(p+".state",CorpseState.CLEAN.name()));ItemStack it=new ItemStack(Material.SKELETON_SKULL);var m=it.getItemMeta();m.displayName(Component.text(data.getString(p+".name","不明")+" - "+st.label,NamedTextColor.RED));m.lore(List.of(Component.text("死亡時HP: "+data.getInt(p+".death-hp"),NamedTextColor.GRAY),Component.text("右クリック: 削除",NamedTextColor.DARK_GRAY)));m.getPersistentDataContainer().set(corpseKey,PersistentDataType.STRING,key);it.setItemMeta(m);inv.setItem(slot++,it);}
        inv.setItem(49,named(Material.BARRIER,"全遺体・血痕を削除")); inv.setItem(53,named(Material.ARROW,"戻る")); kp.openInventory(inv);
    }

    public boolean handleKpClick(Player kp, ItemStack item, int slot, boolean rightClick){
        if(slot==53){plugin.getKpToolManager().open(kp);return true;} if(slot==49){clearAll();openKpGui(kp);return true;}
        if(item==null||!item.hasItemMeta())return true; String raw=item.getItemMeta().getPersistentDataContainer().get(corpseKey,PersistentDataType.STRING); if(raw!=null&&rightClick)try{remove(UUID.fromString(raw));openKpGui(kp);}catch(Exception ignored){} return true;
    }

    private ItemStack named(Material mat,String name){ItemStack i=new ItemStack(mat);var m=i.getItemMeta();m.displayName(Component.text(name,NamedTextColor.RED));i.setItemMeta(m);return i;}
    private boolean canKp(Player p){return p.isOp()||p.hasPermission("trpg.admin")||plugin.getKeeperManager().isKeeper(p);}
    private void cleanupExpiredBlood(){long now=System.currentTimeMillis();ConfigurationSection sec=data.getConfigurationSection("corpses");if(sec==null)return;for(String key:sec.getKeys(false)){if(data.getLong("corpses."+key+".blood-expires",0)>0&&data.getLong("corpses."+key+".blood-expires")<=now){try{UUID id=UUID.fromString(key);SpawnedCorpse sc=spawned.get(id);if(sc!=null){sc.blood.forEach(Entity::remove);sc.blood.clear();}}catch(Exception ignored){} data.set("corpses."+key+".blood-expires",0);}}save();}
    private void respawnAll(){ConfigurationSection sec=data.getConfigurationSection("corpses");if(sec!=null)for(String key:sec.getKeys(false))try{spawn(UUID.fromString(key));}catch(Exception ignored){}}
    public void remove(UUID id){SpawnedCorpse sc=spawned.remove(id);if(sc!=null)sc.remove();data.set("corpses."+id,null);save();}
    public void clearAll(){new ArrayList<>(spawned.values()).forEach(SpawnedCorpse::remove);spawned.clear();data.set("corpses",null);save();}
    public void shutdown(){spawned.values().forEach(SpawnedCorpse::remove);spawned.clear();save();}
    private void save(){try{data.save(file);}catch(IOException e){plugin.getLogger().warning("corpses.yml save failed: "+e.getMessage());}}
    private static class SpawnedCorpse{final Entity body,interaction;final List<Entity> blood;SpawnedCorpse(Entity b,Entity i,List<Entity> bl){body=b;interaction=i;blood=bl;}void remove(){body.remove();interaction.remove();blood.forEach(Entity::remove);}}
}
