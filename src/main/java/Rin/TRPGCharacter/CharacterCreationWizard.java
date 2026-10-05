package Rin.TRPGCharacter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** GUI wizard: stats -> occupation -> occupation skill choices/points -> hobby points -> confirmation. */
public class CharacterCreationWizard implements Listener {
    private static final String ROOT="§0探索者作成ウィザード", OCC="§0職業を選択", CHOICE="§0職業技能を選択";
    private static final String OCCPTS="§0職業技能ポイント", HOBBY="§0趣味技能ポイント", CONFIRM="§0探索者作成確認";
    private final Plugin plugin; private final CharacterManager chars; private final SkillManager skills; private final OccupationManager occupations;
    private final NamespacedKey occKey, skillKey;
    private final Map<UUID,Integer> occPage=new HashMap<>(), hobbyPage=new HashMap<>();
    public CharacterCreationWizard(Plugin plugin, CharacterManager chars, SkillManager skills, OccupationManager occupations){
        this.plugin=plugin;this.chars=chars;this.skills=skills;this.occupations=occupations;
        occKey=new NamespacedKey(plugin,"wizard_occupation"); skillKey=new NamespacedKey(plugin,"wizard_skill");
    }
    public void open(Player p){
        Inventory inv=plugin.getServer().createInventory(null,27,ROOT);
        inv.setItem(10,item(Material.FIREWORK_STAR,"1. 能力値を生成",statLore(p)));
        inv.setItem(12,item(Material.LECTERN,"2. 職業を選択","現在: "+chars.getOccupationName(p)));
        inv.setItem(14,item(Material.EXPERIENCE_BOTTLE,"3. 職業技能ポイント","残り: "+chars.getOccupationPointRemaining(p)+" / "+chars.getOccupationPointTotal(p)));
        inv.setItem(16,item(Material.ENCHANTED_BOOK,"4. 趣味技能ポイント","残り: "+chars.getHobbyPointRemaining(p)+" / "+chars.getHobbyPointTotal(p)));
        inv.setItem(22,item(Material.EMERALD,"5. 確認して完成","現在の探索者を確認")); p.openInventory(inv);
    }
    private void openOccupations(Player p){
        Inventory inv=plugin.getServer().createInventory(null,54,OCC); int slot=0;
        for(OccupationDefinition d:occupations.getDefinitions()){
            if(slot>=45)break; ItemStack it=item(Material.BOOK,d.name(),d.pointFormula(),"クリックで選択"); tag(it,occKey,d.id()); inv.setItem(slot++,it);
        }
        inv.setItem(49,item(Material.ARROW,"戻る")); p.openInventory(inv);
    }
    private void openChoice(Player p){
        OccupationDefinition d=occupations.getDefinition(p); if(d==null||occupations.getOptionalTotalCount(p)==0){openOccPoints(p,0);return;}
        Inventory inv=plugin.getServer().createInventory(null,54,CHOICE); int slot=0;
        for(SkillDefinition s:skills.getAllSkills()){
            if(slot>=45)break; boolean selected=chars.isOccupationSkill(p,s.getId());
            if(!selected && !occupations.isSkillSelectable(p,s.getId())) continue;
            ItemStack it=item(Material.PAPER,(selected?"★ ":"")+s.getName(),"選択: "+occupations.getOptionalSelectedCount(p)+" / "+occupations.getOptionalTotalCount(p),"クリックで選択/解除"); tag(it,skillKey,s.getId()); inv.setItem(slot++,it);
        }
        inv.setItem(49,item(Material.ARROW,"職業選択へ")); inv.setItem(53,item(Material.EMERALD,"ポイント割り振りへ")); p.openInventory(inv);
    }
    private void openOccPoints(Player p,int page){occPage.put(p.getUniqueId(),page);openPoints(p,true,page);}
    private void openHobbyPoints(Player p,int page){hobbyPage.put(p.getUniqueId(),page);openPoints(p,false,page);}
    private void openPoints(Player p,boolean occupation,int page){
        String title=occupation?OCCPTS:HOBBY; Inventory inv=plugin.getServer().createInventory(null,54,title);
        List<SkillDefinition> list=new ArrayList<>(); for(SkillDefinition s:skills.getAllSkills())if(!occupation||chars.isOccupationSkill(p,s.getId()))list.add(s);
        int pages=Math.max(1,(list.size()+44)/45); page=Math.max(0,Math.min(page,pages-1)); if(occupation)occPage.put(p.getUniqueId(),page);else hobbyPage.put(p.getUniqueId(),page);
        for(int i=0;i<45;i++){int idx=page*45+i;if(idx>=list.size())break;SkillDefinition s=list.get(idx);int a=occupation?chars.getOccupationAllocation(p,s.getId()):chars.getHobbyAllocation(p,s.getId());
            ItemStack it=item(Material.PAPER,s.getName(),"現在値: "+skills.getSkillValue(p,s.getId()),"今回割振り: +"+a,"左: +5 / 右: +10");tag(it,skillKey,s.getId());inv.setItem(i,it);}
        int remain=occupation?chars.getOccupationPointRemaining(p):chars.getHobbyPointRemaining(p); int total=occupation?chars.getOccupationPointTotal(p):chars.getHobbyPointTotal(p);
        inv.setItem(45,item(Material.ARROW,"前ページ")); inv.setItem(46,item(Material.ARROW,"次ページ")); inv.setItem(49,item(Material.NETHER_STAR,"残り "+remain+" / "+total,"Shift+クリック: 割り振りをリセット")); inv.setItem(53,item(Material.EMERALD,occupation?"趣味技能へ":"確認へ")); p.openInventory(inv);
    }
    private void openConfirm(Player p){
        Inventory inv=plugin.getServer().createInventory(null,27,CONFIRM);
        inv.setItem(4,item(Material.PLAYER_HEAD,chars.getCharacterName(p),"職業: "+chars.getOccupationName(p)));
        inv.setItem(10,item(Material.FIREWORK_STAR,"能力値",statLore(p))); inv.setItem(12,item(Material.EXPERIENCE_BOTTLE,"職業P","使用: "+chars.getOccupationPointUsed(p)+" / "+chars.getOccupationPointTotal(p)));
        inv.setItem(14,item(Material.ENCHANTED_BOOK,"趣味P","使用: "+chars.getHobbyPointUsed(p)+" / "+chars.getHobbyPointTotal(p)));
        inv.setItem(20,item(Material.ARROW,"戻って修正")); inv.setItem(24,item(Material.EMERALD_BLOCK,"探索者作成を完了","クリックで完成")); p.openInventory(inv);
    }
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;String t=e.getView().getTitle();if(!Set.of(ROOT,OCC,CHOICE,OCCPTS,HOBBY,CONFIRM).contains(t))return;e.setCancelled(true);
        ItemStack it=e.getCurrentItem();if(it==null||!it.hasItemMeta())return;
        if(t.equals(ROOT)){switch(e.getSlot()){case 10-> {plugin.getRandomStatManager().generate(p,false);plugin.getServer().getScheduler().runTaskLater(plugin,()->open(p),2L);}case 12->openOccupations(p);case 14->{if(chars.hasOccupation(p))openChoice(p);else openOccupations(p);}case 16->openHobbyPoints(p,0);case 22->openConfirm(p);}return;}
        if(t.equals(OCC)){if(e.getSlot()==49){open(p);return;}String id=tag(it,occKey);if(id!=null){OccupationDefinition d=occupations.findByInput(id);if(d!=null){chars.setOccupation(p,d.id(),d.name(),false,d.pointFormula(),d.fixedSkills());chars.resetHobbyAllocations(p);openChoice(p);}}return;}
        if(t.equals(CHOICE)){if(e.getSlot()==49){openOccupations(p);return;}if(e.getSlot()==53){openOccPoints(p,0);return;}String id=tag(it,skillKey);if(id!=null){occupations.toggleOccupationSkill(p,id);openChoice(p);}return;}
        if(t.equals(OCCPTS)||t.equals(HOBBY)){boolean oc=t.equals(OCCPTS);int page=oc?occPage.getOrDefault(p.getUniqueId(),0):hobbyPage.getOrDefault(p.getUniqueId(),0);
            if(e.getSlot()==45){if(page>0){if(oc)openOccPoints(p,page-1);else openHobbyPoints(p,page-1);}return;}if(e.getSlot()==46){if(oc)openOccPoints(p,page+1);else openHobbyPoints(p,page+1);return;}if(e.getSlot()==49&&e.isShiftClick()){if(oc)chars.resetOccupationAllocations(p);else chars.resetHobbyAllocations(p);if(oc)openOccPoints(p,page);else openHobbyPoints(p,page);return;}
            if(e.getSlot()==53){if(oc)openHobbyPoints(p,0);else openConfirm(p);return;}String id=tag(it,skillKey);if(id!=null){int add=e.isRightClick()?10:5;boolean ok=oc?chars.addOccupationAllocation(p,id,add):chars.addHobbyAllocation(p,id,add);if(!ok)p.sendMessage("§c残りポイントが不足しているか、この技能には割り振れません。");if(oc)openOccPoints(p,page);else openHobbyPoints(p,page);}return;}
        if(t.equals(CONFIRM)){if(e.getSlot()==20){open(p);return;}if(e.getSlot()==24){p.closeInventory();boolean gaveBook=false,gaveDie=false;if(!hasSheet(p)){p.getInventory().addItem(plugin.getBookManager().createSheet(p));gaveBook=true;}if(!hasSkillDie(p)){p.getInventory().addItem(plugin.getCharacterGuiManager().createSkillDie());gaveDie=true;}p.sendMessage("§6[TRPG] §a探索者データを保存しました。"+(gaveBook||gaveDie?" §7(不足していた専用アイテムのみ補充)":" §7(所持中の本・技能ダイスは再配布しません)"));plugin.getSidebarManager().updatePlayer(p);plugin.getHealthSyncManager().sync(p);}}
    }
    private boolean hasSheet(Player p){for(ItemStack i:p.getInventory().getContents())if(plugin.getBookManager().isCharacterSheet(i))return true;return false;}
    private boolean hasSkillDie(Player p){for(ItemStack i:p.getInventory().getContents())if(plugin.getCharacterGuiManager().isSkillDie(i))return true;return false;}
    private List<String> statLore(Player p){List<String> l=new ArrayList<>();for(String s:new String[]{"STR","CON","POW","DEX","APP","SIZ","INT","EDU"})l.add(s+": "+chars.getStat(p,s));return l;}
    private ItemStack item(Material m,String n,String... lore){return item(m,n,Arrays.asList(lore));} private ItemStack item(Material m,String n,List<String> lore){ItemStack i=new ItemStack(m);ItemMeta im=i.getItemMeta();im.displayName(Component.text(n,NamedTextColor.GOLD));im.lore(lore.stream().map(x->Component.text(x,NamedTextColor.GRAY)).toList());i.setItemMeta(im);return i;}
    private void tag(ItemStack i,NamespacedKey k,String v){ItemMeta m=i.getItemMeta();m.getPersistentDataContainer().set(k,PersistentDataType.STRING,v);i.setItemMeta(m);}private String tag(ItemStack i,NamespacedKey k){return i.getItemMeta().getPersistentDataContainer().get(k,PersistentDataType.STRING);}
}
