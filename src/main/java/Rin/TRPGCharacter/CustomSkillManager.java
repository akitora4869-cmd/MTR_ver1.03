package Rin.TRPGCharacter;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Player-created skills + KP review/session restriction UI. */
public final class CustomSkillManager implements Listener {
    private static final String EDITOR="MCTRPG Custom Skill Editor", KP="MCTRPG Custom Skills - KP", INSPECT="MCTRPG Custom Skills - ";
    private final Plugin plugin; private final SkillManager skills; private final CharacterManager chars;
    private final File file; private YamlConfiguration cfg;
    private final Map<UUID,Draft> drafts=new ConcurrentHashMap<>(); private final Map<UUID,String> awaiting=new ConcurrentHashMap<>();
    private final Map<UUID,UUID> inspecting=new ConcurrentHashMap<>();

    public CustomSkillManager(Plugin plugin, SkillManager skills, CharacterManager chars){
        this.plugin=plugin;this.skills=skills;this.chars=chars;this.file=new File(plugin.getDataFolder(),"custom-skills.yml"); reload();
    }
    public void reload(){cfg=YamlConfiguration.loadConfiguration(file);}
    public void openEditor(Player p){ openEditor(p, false); }
    public void openKpEditor(Player p){
        if(!isKp(p)){p.sendMessage(c("&cKP権限が必要です。"));return;}
        openEditor(p, true);
    }
    private void openEditor(Player p, boolean scenario){
        String prefix=scenario?"scenario_":"custom_";
        Draft d=new Draft(prefix+p.getUniqueId().toString().substring(0,8)+"_"+System.currentTimeMillis());
        d.owner=p.getUniqueId(); d.scenario=scenario; d.name=scenario?"新しいシナリオ技能":"新しい技能";
        drafts.put(p.getUniqueId(),d); render(p);
    }
    private void render(Player p){Draft d=drafts.get(p.getUniqueId());if(d==null)return;Inventory inv=plugin.getServer().createInventory(null,45,EDITOR);
        inv.setItem(10,item(Material.NAME_TAG,"&e技能名","&f"+d.name,"&7クリック→チャット入力"));
        inv.setItem(11,item(Material.EXPERIENCE_BOTTLE,"&e技能値","&f"+d.value,"&7クリック→1～99を入力"));
        inv.setItem(12,item(Material.BOOK,"&eカテゴリ","&f"+d.category,"&7クリックで切替"));
        inv.setItem(13,item(Material.WRITABLE_BOOK,"&e説明","&f"+(d.description.isBlank()?"未設定":d.description),"&7クリック→チャット入力"));
        inv.setItem(19,item(d.scenario?Material.PURPLE_DYE:Material.LIME_DYE,d.scenario?"&dKPシナリオ技能":"&aかんたん作成",d.scenario?"&7KP作成。探索者へ任意配布できます。":"&7判定だけの技能なら、この4項目だけで完成できます。"));
        inv.setItem(21,item(Material.IRON_SWORD,"&c成功効果: ダメージ","&7複数効果を重ねられます","&7クリック→例 1d6+2"));
        inv.setItem(22,item(Material.GOLDEN_APPLE,"&a成功効果: 回復","&7クリック→例 1d3+1"));
        inv.setItem(23,item(Material.ENDER_PEARL,"&b成功効果: TP","&7現在地点を登録"));
        inv.setItem(24,item(Material.ENDER_EYE,"&5成功効果: SAN変動","&7クリック→例 -1 / 1d3"));
        inv.setItem(25,item(Material.POTION,"&d成功効果: Potion","&7クリック→ EFFECT,秒,強度","&8例 SPEED,10,1"));
        inv.setItem(28,item(Material.OAK_SIGN,"&e成功メッセージ","&7クリック→チャット入力"));
        inv.setItem(29,item(Material.NOTE_BLOCK,"&e成功サウンド","&7クリック→Sound名"));
        inv.setItem(30,item(Material.BLAZE_POWDER,"&e成功パーティクル","&7クリック→Particle名"));
        inv.setItem(31,item(Material.TARGET,"&6効果対象","&f"+(d.targetLook?"視線先の対象":"自分"),"&7クリックで切替"));
        inv.setItem(32,item(Material.BARRIER,"&c効果を全削除","&7現在: "+d.effects.size()+"個"));
        inv.setItem(40,item(Material.LIME_CONCRETE,d.scenario?"&aシナリオ技能として保存":"&a保存して習得","&7ID: &f"+d.id,d.scenario?"&7保存後 /customskill give で配布":"&7保存後、KPから確認・禁止可能"));
        p.openInventory(inv);
    }
    public void openKp(Player p){if(!isKp(p)){p.sendMessage(c("&cKP権限が必要です。"));return;}Inventory inv=plugin.getServer().createInventory(null,54,KP);int slot=0;
        for(String id:cfg.getConfigurationSection("skills")==null?Set.<String>of():cfg.getConfigurationSection("skills").getKeys(false)){if(slot>=45)break;String b="skills."+id;String name=cfg.getString(b+".name",id);boolean blocked=cfg.getBoolean(b+".blocked",false);List<String> owners=cfg.getStringList(b+".owners");inv.setItem(slot++,tag(Material.ENCHANTED_BOOK,(blocked?"&c[禁止] ":"&a[許可] ")+name,List.of("&7ID: &f"+id,"&7所有者: &f"+owners.size()+"人","&e左クリック: シナリオ使用 許可/禁止","&b右クリック: 詳細"),id));}
        inv.setItem(49,item(Material.NETHER_STAR,"&dKP: シナリオ技能を新規作成","&7KP専用技能エディタを開きます"));
        p.openInventory(inv);
    }
    public void inspect(Player kp,Player target){if(!isKp(kp)){kp.sendMessage(c("&cKP権限が必要です。"));return;}inspecting.put(kp.getUniqueId(),target.getUniqueId());Inventory inv=plugin.getServer().createInventory(null,54,INSPECT+target.getName());int slot=0;
        for(String id:getOwnedSkillIds(target)){if(slot>=45)break;String b="skills."+id;boolean blocked=cfg.getBoolean(b+".blocked",false);List<String> lore=new ArrayList<>();lore.add("&7技能値: &f"+chars.getStoredSkill(target,id));lore.add("&7説明: &f"+cfg.getString(b+".description",""));lore.add("&7効果数: &f"+cfg.getMapList(b+".effects").size());lore.add(blocked?"&c現在シナリオ使用禁止":"&a現在使用可能");lore.add("&eクリック: 許可/禁止を切替");inv.setItem(slot++,tag(Material.PAPER,(blocked?"&c":"&a")+cfg.getString(b+".name",id),lore,id));}kp.openInventory(inv);
    }
    public boolean isCustom(String id){return cfg.contains("skills."+id);}
    public boolean owns(Player p,String id){return cfg.getStringList("skills."+id+".owners").contains(p.getUniqueId().toString());}
    public boolean canUse(Player p,String id){if(!isCustom(id))return true;if(!owns(p,id)){p.sendMessage(c("&cこのオリジナル技能は習得していません。"));return false;}if(cfg.getBoolean("skills."+id+".blocked",false)){p.sendMessage(c("&cこのオリジナル技能は現在のシナリオではKPにより使用禁止です。"));return false;}return true;}
    public List<String> getOwnedSkillIds(Player p){List<String> out=new ArrayList<>();ConfigurationSection s=cfg.getConfigurationSection("skills");if(s!=null)for(String id:s.getKeys(false))if(owns(p,id))out.add(id);return out;}
    public void applySuccess(Player p, String id) {
        if (!isCustom(id)) return;
        String base = "skills." + id;
        boolean look = cfg.getBoolean(base + ".target-look", false);
        LivingEntity target = p;
        if (look) {
            var ent = p.getTargetEntity(6);
            if (ent instanceof LivingEntity le) target = le;
        }
        for (Map<?, ?> raw : cfg.getMapList(base + ".effects")) {
            String type = String.valueOf(raw.get("type"));
            String value = String.valueOf(raw.get("value"));
            try {
                switch (type) {
                    case "DAMAGE" -> target.damage(roll(value), p);
                    case "HEAL" -> {
                        var attr = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                        double max = attr == null ? target.getHealth() : attr.getValue();
                        target.setHealth(Math.min(max, target.getHealth() + roll(value)));
                    }
                    case "SAN" -> {
                        int delta = signedRoll(value);
                        chars.setCurrentSan(p, Math.max(0, Math.min(chars.getSan(p), chars.getCurrentSan(p) + delta)));
                    }
                    case "TP" -> {
                        World w = Bukkit.getWorld(String.valueOf(raw.get("world")));
                        if (w != null) p.teleport(new Location(w, d(raw,"x"), d(raw,"y"), d(raw,"z")));
                    }
                    case "MESSAGE" -> p.sendMessage(c("&d[" + cfg.getString(base + ".name", id) + "] &f" + value));
                    case "SOUND" -> p.playSound(p.getLocation(), Sound.valueOf(value.toUpperCase(Locale.ROOT)), 1f, 1f);
                    case "PARTICLE" -> p.getWorld().spawnParticle(Particle.valueOf(value.toUpperCase(Locale.ROOT)), target.getLocation().add(0,1,0), 20, 0.4,0.4,0.4,0.03);
                    case "POTION" -> {
                        String[] parts = value.split(",");
                        PotionEffectType t = PotionEffectType.getByName(parts[0].trim().toUpperCase(Locale.ROOT));
                        if (t != null) target.addPotionEffect(new PotionEffect(t, Integer.parseInt(parts[1].trim()) * 20, Math.max(0, Integer.parseInt(parts[2].trim()) - 1)));
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Custom skill effect failed " + id + "/" + type + ": " + ex.getMessage());
            }
        }
    }
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getWhoClicked() instanceof Player p))return;String title=e.getView().getTitle();if(title.equals(EDITOR)){e.setCancelled(true);Draft d=drafts.get(p.getUniqueId());if(d==null)return;switch(e.getRawSlot()){case 10->ask(p,"name","技能名を入力してください。");case 11->ask(p,"value","技能値 1～99 を入力してください。");case 12->{String[] cs={"オリジナル技能","戦闘技能","探索技能","行動技能","交渉技能","知識技能"};int i=Arrays.asList(cs).indexOf(d.category);d.category=cs[(i+1)%cs.length];render(p);}case 13->ask(p,"desc","技能の説明を入力してください。");case 21->ask(p,"damage","ダメージ式を入力してください（例 1d6+2）。");case 22->ask(p,"heal","回復式を入力してください（例 1d3+1）。");case 23->{Location l=p.getLocation();Map<String,Object> m=new LinkedHashMap<>();m.put("type","TP");m.put("world",l.getWorld().getName());m.put("x",l.getX());m.put("y",l.getY());m.put("z",l.getZ());d.effects.add(m);render(p);}case 24->ask(p,"san","SAN変動を入力してください（例 -1 / 1d3）。");case 25->ask(p,"potion","Potionを EFFECT,秒,強度 で入力（例 SPEED,10,1）。");case 28->ask(p,"message","成功時メッセージを入力してください。");case 29->ask(p,"sound","Sound名を入力してください。");case 30->ask(p,"particle","Particle名を入力してください。");case 31->{d.targetLook=!d.targetLook;render(p);}case 32->{d.effects.clear();render(p);}case 40->save(p,d);}}
        else if(title.equals(KP)||title.startsWith(INSPECT)){e.setCancelled(true);if(title.equals(KP)&&e.getRawSlot()==49){openKpEditor(p);return;}ItemStack it=e.getCurrentItem();if(it==null||!it.hasItemMeta())return;String id=ChatColor.stripColor(it.getItemMeta().getLore()==null?"":it.getItemMeta().getLore().stream().filter(x->ChatColor.stripColor(x).startsWith("ID: ")).findFirst().orElse("")).replace("ID: ","");if(id.isBlank()&&title.startsWith(INSPECT)){String n=ChatColor.stripColor(it.getItemMeta().getDisplayName());id=findByName(n);}if(id.isBlank())return;if(e.isRightClick()&&title.equals(KP)){p.sendMessage(c("&d[Custom Skill] &f"+cfg.getString("skills."+id+".name",id)+" &7- "+cfg.getString("skills."+id+".description","説明なし")+" / effects="+cfg.getMapList("skills."+id+".effects").size()));return;}toggleBlocked(id);if(title.equals(KP))openKp(p);else{UUID u=inspecting.get(p.getUniqueId());Player t=u==null?null:Bukkit.getPlayer(u);if(t!=null)inspect(p,t);}}
    }
    @EventHandler public void chat(AsyncPlayerChatEvent e){String key=awaiting.remove(e.getPlayer().getUniqueId());if(key==null)return;e.setCancelled(true);String s=e.getMessage();Player p=e.getPlayer();Bukkit.getScheduler().runTask(plugin,()->{Draft d=drafts.get(p.getUniqueId());if(d==null)return;if(!s.equalsIgnoreCase("cancel")){try{switch(key){case"name"->d.name=s;case"value"->d.value=Math.max(1,Math.min(99,Integer.parseInt(s)));case"desc"->d.description=s;case"damage"->d.effects.add(effect("DAMAGE",s));case"heal"->d.effects.add(effect("HEAL",s));case"san"->d.effects.add(effect("SAN",s));case"potion"->d.effects.add(effect("POTION",s));case"message"->d.effects.add(effect("MESSAGE",s));case"sound"->d.effects.add(effect("SOUND",s));case"particle"->d.effects.add(effect("PARTICLE",s));}}catch(Exception ex){p.sendMessage(c("&c入力値が不正です。"));}}render(p);});}
    private void save(Player p,Draft d){
        String b="skills."+d.id;
        cfg.set(b+".name",d.name);cfg.set(b+".default",0);cfg.set(b+".category",d.category);cfg.set(b+".description",d.description);
        cfg.set(b+".target-look",d.targetLook);cfg.set(b+".effects",d.effects);cfg.set(b+".creator",p.getUniqueId().toString());
        cfg.set(b+".kind",d.scenario?"SCENARIO":"CUSTOM");cfg.set(b+".session-only",d.scenario);cfg.set(b+".owners",d.scenario?List.of():List.of(p.getUniqueId().toString()));cfg.set(b+".blocked",false);
        saveCfg();
        if(!d.scenario) chars.setSkill(p,d.id,d.value);
        skills.reload();drafts.remove(p.getUniqueId());p.closeInventory();
        if(d.scenario)p.sendMessage(c("&aシナリオ技能「&f"+d.name+"&a」を作成しました。 &7/customskill give <player> "+d.id+" [値] で付与できます。"));
        else p.sendMessage(c("&aオリジナル技能「&f"+d.name+"&a」を作成・習得しました。 &7KPは /customskill kp から確認・制限できます。"));
    }
    public boolean grant(Player kp, Player target, String id, Integer overrideValue){
        if(!isKp(kp)||!isCustom(id))return false;String b="skills."+id;List<String> owners=new ArrayList<>(cfg.getStringList(b+".owners"));String u=target.getUniqueId().toString();if(!owners.contains(u))owners.add(u);cfg.set(b+".owners",owners);saveCfg();
        int value=overrideValue==null?Math.max(1,cfg.getInt(b+".grant-value",50)):Math.max(1,Math.min(99,overrideValue));chars.setSkill(target,id,value);skills.reload();
        target.sendMessage(c("&d[Custom Skill] &f"+cfg.getString(b+".name",id)+" &aを習得しました。 &7("+value+"%)"));return true;
    }
    public void cleanupSessionSkills(){
        ConfigurationSection sec=cfg.getConfigurationSection("skills");if(sec==null)return;for(String id:sec.getKeys(false)){String b="skills."+id;if(!cfg.getBoolean(b+".session-only",false))continue;cfg.set(b+".owners",new ArrayList<>());}
        saveCfg();skills.reload();
    }
    private void toggleBlocked(String id){cfg.set("skills."+id+".blocked",!cfg.getBoolean("skills."+id+".blocked",false));saveCfg();}
    private String findByName(String n){ConfigurationSection s=cfg.getConfigurationSection("skills");if(s!=null)for(String id:s.getKeys(false))if(cfg.getString("skills."+id+".name",id).equals(n.replace("[禁止] ","").replace("[許可] ","")))return id;return "";}
    private void saveCfg(){try{cfg.save(file);}catch(Exception ex){plugin.getLogger().severe("custom-skills.yml save failed: "+ex.getMessage());}}
    private boolean isKp(Player p){return p.isOp()||p.hasPermission("trpg.admin")||plugin.getKeeperManager().isKeeper(p);}
    private void ask(Player p,String k,String msg){awaiting.put(p.getUniqueId(),k);p.closeInventory();p.sendMessage(c("&5[Custom Skill Editor] &f"+msg+" &7(cancelで取消)"));}
    private Map<String,Object> effect(String t,String v){Map<String,Object> m=new LinkedHashMap<>();m.put("type",t);m.put("value",v);return m;}
    private int roll(String s){s=s.trim();try{return Integer.parseInt(s);}catch(Exception ignored){}java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\d+)[dD](\\d+)([+-]\\d+)?").matcher(s);if(!m.matches())return 0;int n=Integer.parseInt(m.group(1)),side=Integer.parseInt(m.group(2)),sum=0;for(int i=0;i<n;i++)sum+=1+new Random().nextInt(side);if(m.group(3)!=null)sum+=Integer.parseInt(m.group(3));return sum;}
    private int signedRoll(String s){if(s.startsWith("-"))return-roll(s.substring(1));if(s.startsWith("+"))s=s.substring(1);return roll(s);}
    private double d(Map<?,?>m,String k){return Double.parseDouble(String.valueOf(m.get(k)));}
    private ItemStack item(Material m,String n,String...l){return item(m,n,Arrays.asList(l));}private ItemStack item(Material m,String n,List<String>l){ItemStack i=new ItemStack(m);ItemMeta im=i.getItemMeta();im.setDisplayName(c(n));im.setLore(l.stream().map(this::c).toList());i.setItemMeta(im);return i;}
    private ItemStack tag(Material m,String n,List<String>l,String id){List<String>x=new ArrayList<>(l);x.add(0,"&7ID: &f"+id);return item(m,n,x);}private String c(String s){return ChatColor.translateAlternateColorCodes('&',s);}
    private static final class Draft{final String id;UUID owner;String name="新しい技能",category="オリジナル技能",description="";int value=50;boolean targetLook=false,scenario=false;final List<Map<String,Object>>effects=new ArrayList<>();Draft(String id){this.id=id;}}
}
