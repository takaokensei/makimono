# Evidência Decompilada: MainActivity

**Evidence ID:** `ev_07b5781d17b0cf694ed5db3060363212824cd3bcb7fc0e5261e3ba25216468b0`  
**Classe:** `com.crunchyroll.crunchyroid.main.ui.MainActivity`  
**Fonte:** JADX Headless MCP Engine v0.7.1 via REA v6.4.0  
**Enquadramento:** `[IDEIA]` Arquitetura Compose Host e Injeção de Estado.

---

## Assinatura e Campos Observados

```text
Class: com.crunchyroll.crunchyroid.main.ui.MainActivity
Superclass: com.crunchyroll.crunchyroid.main.ui.Hilt_MainActivity -> FragmentActivity -> ComponentActivity

Campos Hilt e Infraestrutura:
- NetworkManager (com.crunchyroll.connectivity.NetworkManager)
- AppPreferences (com.crunchyroll.api.repository.preferences.AppPreferences)
- WatchNextManager (com.crunchyroll.workmanager.WatchNextManager)
- InAppReviewFeature (com.crunchyroll.inappreview.InAppReviewFeature)
- HiltWorkerFactory (androidx.hilt.work.HiltWorkerFactory)
```

---

## Método `onCreate` Decompilado

```java
@Override
protected void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    WindowCompat.b(getWindow(), false);
    a1().d(this);
    Z0().b(this);
    final String stringExtra = getIntent().getStringExtra("MainActivityStartDestination");
    if (stringExtra == null) {
        stringExtra = StringUtils.f50046a.g().invoke();
    }
    Timber.Forest forest = Timber.f98442a;
    String mainActivityLogTag = this.X;
    Intrinsics.g(mainActivityLogTag, "mainActivityLogTag");
    forest.t(mainActivityLogTag).i("MainActivity startDestination = " + stringExtra, new Object[0]);
    String mainActivityLogTag2 = this.X;
    Intrinsics.g(mainActivityLogTag2, "mainActivityLogTag");
    forest.t(mainActivityLogTag2).i("DeepLink destination = " + b1().w().getValue(), new Object[0]);
    final String stringExtra2 = getIntent().getStringExtra("SettingsAppLanguageChangeDeeplink");
    o1();
    K0(ComposableLambdaKt.c(626118510, true, new Function2() {
        @Override
        public final Object invoke(Object obj, Object obj2) {
            return MainActivity.e1(stringExtra, this, stringExtra2, (Composer) obj, ((Integer) obj2).intValue());
        }
    }));
}
```

---

## Fato Observado e Conclusão

1. A Activity não infla qualquer arquivo XML de layout (`setContentView(R.layout...)`).
2. O método `K0(...)` chama diretamente o runtime do Jetpack Compose (`ComponentActivity.setContent`), passando um Composable lambda com chave de recomposição `626118510`.
3. Os estados observados no nível raiz incluem `LupinListState`, `TvPromotionalAsset` e `WatchNextManager`.
