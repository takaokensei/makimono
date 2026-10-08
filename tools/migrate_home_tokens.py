import re

files = [
    r'app\src\main\res\layout-land\fragment_home.xml',
    r'app\src\main\res\layout-television\fragment_home.xml',
    r'app\src\main\res\layout-sw720dp-land\fragment_home.xml',
    r'app\src\main\res\layout-port\fragment_home.xml'
]

replacements = [
    ('android:textSize="20sp"', 'style="@style/TextAppearance.Makimono.TV.Title3"'),
    ('android:textSize="18sp"', 'style="@style/TextAppearance.Makimono.TV.Headline"'),
    ('android:textSize="17sp"', 'style="@style/TextAppearance.Makimono.TV.Headline"'),
    ('android:textSize="13sp"', 'style="@style/TextAppearance.Makimono.TV.Callout"'),
    ('android:textSize="12sp"', 'style="@style/TextAppearance.Makimono.TV.Body"'),
    ('android:textSize="11sp"', 'style="@style/TextAppearance.Makimono.TV.Caption"'),
    ('android:textSize="10sp"', 'style="@style/TextAppearance.Makimono.TV.Badge"')
]

for f in files:
    try:
        content = open(f, 'r', encoding='utf-8').read()
        for old, new in replacements:
            content = content.replace(old, new)
        open(f, 'w', encoding='utf-8').write(content)
        print('Updated', f)
    except Exception as e:
        print('Error on', f, e)
