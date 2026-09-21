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

h=E.parse(R/'layout-television/fragment_home.xml').getroot()
setv(find(h,'navRail'), layout_width='128dp', paddingTop='16dp', paddingBottom='12dp', elevation='0dp')
brand=find(h,'btnBrandLogo')
setv(brand, layout_height='80dp', orientation='vertical', gravity='center', paddingStart='0dp', paddingEnd='0dp')
logo=find(h,'ivAppLogo');setv(logo, layout_width='44dp',layout_height='44dp',src='@drawable/ic_makimono_torii')
brand.remove(brand[1])
E.SubElement(brand,'TextView',{a('layout_width'):'wrap_content',a('layout_height'):'wrap_content',a('text'):'MAKIMONO',a('letterSpacing'):'0.28',a('textColor'):'#E3EDFF',a('textSize'):'10sp',a('layout_marginTop'):'6dp'})
rail=find(h,'navRail');rail.remove(rail[1])
for id in ['btnNavInicio','btnNavAnimes','btnNavPastas','btnNavFavoritos','btnNavConfig']:
    n=find(h,id);setv(n,layout_height='44dp',paddingStart='10dp',paddingEnd='4dp')
    for c in n:
        if c.tag=='ImageView':setv(c,layout_width='20dp',layout_height='20dp')
        if c.tag=='TextView':setv(c,textSize='12sp',layout_marginStart='12dp')
setv(find(h,'btnNavInicio'),layout_marginTop='14dp')
setv(find(h,'tvNavAnimes'),text='Catálogo')
setv(find(h,'userProfilePill'),layout_height='42dp',paddingStart='10dp',paddingEnd='4dp')
setv(find(h,'tvUserName'),textSize='11sp')
setv(find(h,'contentArea'),paddingStart='22dp',paddingEnd='22dp',paddingTop='12dp',paddingBottom='0dp')
header=find(h,'topHeaderBar');header.remove(header[0]);setv(header,layout_height='40dp')
setv(find(h,'searchPillContainer'),layout_marginStart='0dp',layout_height='36dp')
setv(find(h,'etSearchAnime'),textSize='12sp',hint='Buscar animes, gêneros, personagens…')
hero=find(h,'featuredHeroContainer');setv(hero,layout_height='212dp',layout_marginBottom='4dp')
hero.set(b('cardCornerRadius'),'0dp');hero.set(b('strokeWidth'),'0dp');hero.set(b('cardBackgroundColor'),'#030C16')
frame=hero[0]
for c in frame:
    if c.get(a('layout_width'))=='620dp':setv(c,layout_width='360dp')
textcol=frame[-1];setv(textcol,paddingStart='0dp',paddingEnd='14dp')
setv(find(h,'tvFeaturedTitle'),textSize='27sp',layout_marginTop='4dp')
setv(find(h,'tvFeaturedOverline'),textSize='10sp',textColor='#B9CADF')
setv(find(h,'tvFeaturedJapaneseTitle'),textSize='12sp')
setv(find(h,'tvFeaturedSynopsis'),textSize='12sp',maxLines='3',lineSpacingExtra='1dp',layout_marginTop='6dp')
setv(find(h,'layoutFeaturedGenres'),layout_marginTop='6dp')
setv(textcol[-1],layout_marginTop='10dp')
for id in ['btnFeaturedPlay','btnFeaturedInfo']:setv(find(h,id),layout_height='36dp',paddingStart='14dp',paddingEnd='14dp')
sh=find(h,'shelfHeaderRow');setv(sh,layout_marginBottom='6dp')
sh[0].remove(sh[0][1])
setv(find(h,'tvShelfLabel'),textSize='17sp',letterSpacing='0',text='Continuar assistindo')
for id in ['tvShelfViewHistory','tvShelfViewQueue']:setv(find(h,id),textSize='10sp',minHeight='28dp',gravity='center_vertical')
for id in ['rvContinueWatchingShelf','rvWatchQueueShelf']:
    setv(find(h,id),layout_height='108dp',layout_marginBottom='8dp',paddingTop='4dp',paddingBottom='4dp')
rv=find(h,'rvAnimeLibrary');parent=next(p for p in h.iter() if rv in list(p))
label=E.Element('TextView',{a('id'):'@+id/tvHomeCatalogTitle',a('layout_width'):'match_parent',a('layout_height'):'wrap_content',a('text'):'Em alta / Catálogo',a('textColor'):'#F1F6FF',a('textSize'):'17sp',a('textStyle'):'bold',a('layout_marginTop'):'8dp',a('layout_marginBottom'):'6dp'})
parent.insert(list(parent).index(rv),label)
setv(rv,layout_marginTop='0dp',padding='0dp')
for folder in ['layout-land','layout-sw720dp-land','layout-television']: save(h,R/folder/'fragment_home.xml')

# A single rectangular profile surface, with the focus ring around the whole card.
p=E.parse(R/'layout/item_profile_card.xml').getroot()
setv(p,padding='5dp',background='@drawable/bg_profile_surface')
avatar=find(p,'avatarContainer');setv(avatar,background='@null',padding='0dp')
setv(find(p,'tvProfileName'),layout_width='@dimen/profile_avatar_size',layout_height='24dp',layout_marginTop='6dp',ellipsize='end',textSize='14sp')
setv(find(p,'tvProfileRole'),layout_height='20dp',layout_marginTop='0dp',textSize='11sp',gravity='center',paddingStart='12dp',paddingEnd='12dp',background='@drawable/bg_glass_pill')
save(p,R/'layout/item_profile_card.xml')

p=E.parse(R/'layout/fragment_profile_selection.xml').getroot()
title=find(p,'tvProfilesTitle');setv(title,layout_marginTop='0dp',textSize='28sp',textStyle='normal')
title.attrib.pop(b('layout_constraintTop_toTopOf'),None)
title.set(b('layout_constraintTop_toBottomOf'),'@id/profileBrand')
brand=E.Element('LinearLayout',{a('id'):'@+id/profileBrand',a('layout_width'):'wrap_content',a('layout_height'):'100dp',a('layout_marginTop'):'24dp',a('orientation'):'vertical',a('gravity'):'center',b('layout_constraintTop_toTopOf'):'parent',b('layout_constraintStart_toStartOf'):'parent',b('layout_constraintEnd_toEndOf'):'parent'})
E.SubElement(brand,'ImageView',{a('layout_width'):'64dp',a('layout_height'):'54dp',a('src'):'@drawable/ic_makimono_torii',a('contentDescription'):'@null'})
E.SubElement(brand,'TextView',{a('layout_width'):'wrap_content',a('layout_height'):'wrap_content',a('layout_marginTop'):'12dp',a('text'):'MAKIMONO',a('letterSpacing'):'0.3',a('textSize'):'16sp',a('textColor'):'#E3EDFF'})
p.insert(4,brand)
rv=find(p,'rvProfiles');rv.set(b('layout_constraintWidth_max'),'660dp');rv.set(b('layout_constraintVertical_bias'),'0.40')
setv(find(p,'btnManageProfiles'),text='Gerenciar perfis',textAllCaps='false',letterSpacing='0',layout_height='44dp',layout_marginBottom='4dp')
setv(find(p,'btnProfileSettings'),layout_marginBottom='12dp',minHeight='32dp',textSize='11sp')
save(p,R/'layout-land/fragment_profile_selection.xml')

for folder in ['values-land','values-sw600dp-land','values-television']:
    path=R/folder/'dimens.xml'
    d=E.parse(path).getroot() if path.exists() else E.Element('resources')
    for name,value in {'tv_nav_rail_width':'128dp','tv_shelf_card_width':'176dp','tv_shelf_card_height':'99dp','profile_avatar_size':'128dp','profile_card_margin_horizontal':'9dp','profile_card_margin_vertical':'8dp'}.items():
        n=next((n for n in d if n.get('name')==name),None)
        if n is None:n=E.SubElement(d,'dimen',{'name':name})
        n.text=value
    save(d,path)
