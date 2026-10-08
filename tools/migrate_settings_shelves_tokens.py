files = [
    r'app\src\main\res\layout\fragment_settings.xml',
    r'app\src\main\res\layout\dialog_add_profile.xml',
    r'app\src\main\res\layout\dialog_update_progress.xml',
    r'app\src\main\res\layout\item_continue_watching_shelf.xml',
    r'app\src\main\res\layout-land\item_continue_watching_shelf.xml',
    r'app\src\main\res\layout\item_drive_file_grid.xml',
    r'app\src\main\res\layout\dialog_mal_rating.xml',
    r'app\src\main\res\layout\dialog_player_glass_menu.xml',
    r'app\src\main\res\layout-land\fragment_profile_selection.xml',
    r'app\src\main\res\layout\item_player_drawer_episode.xml'
]

replacements = [
    ('android:textSize="20sp"', 'style="@style/TextAppearance.Makimono.TV.Title3"'),
    ('android:textSize="18sp"', 'style="@style/TextAppearance.Makimono.TV.Headline"'),
    ('android:textSize="17sp"', 'style="@style/TextAppearance.Makimono.TV.Headline"'),
    ('android:textSize="16sp"', 'style="@style/TextAppearance.Makimono.TV.Headline"'),
    ('android:textSize="15sp"', 'style="@style/TextAppearance.Makimono.TV.Subheadline"'),
    ('android:textSize="14sp"', 'style="@style/TextAppearance.Makimono.TV.Subheadline"'),
    ('android:textSize="13sp"', 'style="@style/TextAppearance.Makimono.TV.Callout"'),
    ('android:textSize="12sp"', 'style="@style/TextAppearance.Makimono.TV.Body"'),
    ('android:textSize="11sp"', 'style="@style/TextAppearance.Makimono.TV.Caption"'),
    ('android:textSize="10sp"', 'style="@style/TextAppearance.Makimono.TV.Badge"'),
    ('android:textSize="9sp"', 'style="@style/TextAppearance.Makimono.TV.Badge"')
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
