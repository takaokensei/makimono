"""One-time resource transformation for the landscape streaming shell."""
from pathlib import Path
import xml.etree.ElementTree as E

R = Path('app/src/main/res')
A = 'http://schemas.android.com/apk/res/android'
B = 'http://schemas.android.com/apk/res-auto'
E.register_namespace('android', A)
E.register_namespace('app', B)
E.register_namespace('tools', 'http://schemas.android.com/tools')
def a(k): return '{'+A+'}'+k
def b(k): return '{'+B+'}'+k
def find(root, id): return next(n for n in root.iter() if n.get(a('id'), '').split('/')[-1] == id)
def setv(n, **kw):
    for k,v in kw.items(): n.set(a(k),v)
def save(root, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    E.indent(root, space='    ')
    E.ElementTree(root).write(path, encoding='utf-8', xml_declaration=True)

import copy

s=E.parse(R/'layout/fragment_series_detail.xml').getroot()
setv(s,background='#030C16')
for id in ['ivHeroBackdrop','viewDynamicColorTint']:setv(find(s,id),layout_height='310dp')
setv(find(s,'viewHeroScrim'),layout_height='310dp')
setv(find(s,'topHeaderBar'),layout_marginStart='20dp',layout_marginEnd='20dp',layout_marginTop='12dp')
hero=find(s,'heroContentBlock');setv(hero,layout_marginStart='20dp',layout_marginEnd='20dp',layout_marginTop='14dp',gravity='top')
poster=find(s,'cardSeriesPoster');setv(poster,layout_width='144dp',layout_height='214dp');poster.set(b('cardCornerRadius'),'8dp')
col=hero[1];setv(col,layout_marginStart='24dp')
native=find(s,'tvJapaneseTitle');col.remove(native);col.insert(1,native)
setv(find(s,'tvRomajiTitle'),textSize='28sp',maxLines='2',ellipsize='end')
setv(native,textSize='18sp',layout_marginTop='5dp')
setv(find(s,'tvSynopsis'),textSize='12sp',maxLines='3',ellipsize='end',layout_marginTop='10dp')
for id in ['metaRow1','metaRow2']:setv(find(s,id),layout_marginTop='8dp')
actions=find(s,'actionsRow');setv(actions,layout_marginTop='12dp')
# Preserve every action; let the row scroll on smaller landscape displays.
parent=next(p for p in s.iter() if actions in list(p));index=list(parent).index(actions);parent.remove(actions)
scroll=E.Element('HorizontalScrollView',{a('layout_width'):'match_parent',a('layout_height'):'wrap_content',a('fillViewport'):'false',a('scrollbars'):'none',a('clipToPadding'):'false'})
scroll.append(actions);parent.insert(index,scroll)
for id in ['btnPrimaryAction','btnTrailer','btnFavorite','btnFollow','btnMarkWatched']:
    setv(find(s,id),layout_height='40dp',paddingStart='12dp',paddingEnd='12dp')
for id in ['rvSeasonTabs','rvEpisodes']:
    setv(find(s,id),paddingStart='188dp',paddingEnd='20dp',layout_marginTop='12dp')
save(s,R/'layout-land/fragment_series_detail.xml')
e=E.parse(R/'layout/item_series_episode_card.xml').getroot()
setv(e,layout_width='150dp',layout_height='145dp',layout_marginEnd='10dp');e.set(b('cardCornerRadius'),'7dp')
setv(e[0],padding='3dp');setv(e[0][0],layout_height='81dp')
save(e,R/'layout-land/item_series_episode_card.xml')

f=E.parse(R/'layout/fragment_files.xml').getroot()
outer=E.Element('LinearLayout',{a('layout_width'):'match_parent',a('layout_height'):'match_parent',a('orientation'):'horizontal',a('background'):'#030C16'})
h=E.parse(R/'layout-land/fragment_home.xml').getroot()
rail=copy.deepcopy(find(h,'navRail'))
ids={'navRail':'filesNavRail','btnBrandLogo':'filesBrand','btnNavInicio':'filesHome','btnNavAnimes':'filesCatalog','btnNavPastas':'filesFolders','btnNavFavoritos':'filesFavorites','btnNavConfig':'filesSettings'}
for n in rail.iter():
    old=n.get(a('id'),'').split('/')[-1]
    if old in ids:n.set(a('id'),'@+id/'+ids[old])
    else:n.attrib.pop(a('id'),None)
    if old=='btnNavInicio':setv(n,background='@drawable/rail_item_focus_bg')
    if old=='btnNavPastas':setv(n,background='@drawable/nav_item_active_bg')
rail.remove(rail[-2]) # profile is managed from Home
outer.append(rail);outer.append(f)
setv(f,layout_width='0dp',layout_weight='1',background='#030C16',paddingStart='16dp',paddingEnd='16dp',paddingTop='10dp')
bar=find(f,'appBarLayout');setv(bar,background='#030C16',elevation='0dp')
toolbar=find(f,'toolbar');search=find(f,'searchContainer');bar.remove(toolbar);bar.remove(search)
row=E.Element('LinearLayout',{a('layout_width'):'match_parent',a('layout_height'):'48dp',a('gravity'):'center_vertical',a('orientation'):'horizontal'})
setv(toolbar,layout_width='0dp',layout_weight='1',layout_height='44dp')
setv(search,layout_width='300dp',layout_height='36dp',layout_marginStart='8dp',layout_marginEnd='0dp',layout_marginBottom='0dp')
row.extend([toolbar,search]);bar.insert(0,row)
setv(find(f,'etSearch'),textSize='12sp',hint='Pesquisar neste diretório…')
for id in ['btnToggleGrid','btnClearSearch']:setv(find(f,id),layout_width='36dp',layout_height='36dp',padding='8dp')
setv(find(f,'rvList'),padding='0dp',clipToPadding='false')
save(outer,R/'layout-land/fragment_files.xml')
