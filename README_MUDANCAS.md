# O que foi feito neste fork

Base: [itszechs/DriveStream](https://github.com/itszechs/DriveStream) (Apache-2.0), clonado e adaptado.

## 1. Controles de anime (o motivo do projeto)
No player (`PlayerActivity.kt` + `player_control_view.xml`), foram adicionados dois botões
novos ao lado do play/pause:

- **+90s** — pula a abertura
- **-90s** — desfaz, caso pule demais

A quantidade é uma constante no topo do `PlayerActivity.kt`:

```kotlin
const val SKIP_INTRO_MS = 90_000L
```

Se quiser 85s, 100s, etc., é só mudar esse número. O seek tem trava de segurança —
não deixa passar do fim do vídeo nem voltar antes do início.

## 2. Escopo do Google Drive reduzido
Trocado de `drive` (leitura + escrita) para `drive.readonly` em `strings.xml`.

**Trade-off:** o recurso de favoritar arquivo (★) do app original faz uma escrita
(`PATCH`) no Drive e vai parar de funcionar com esse escopo mais restrito. Se você usa
esse recurso, é só reverter essa linha em `strings.xml`:

```xml
<string name="drive_scope">https://www.googleapis.com/auth/drive</string>
```

## 3. AdMob removido por completo
Não fazia sentido manter anúncios em um app de uso pessoal. Foram removidos:
- Inicialização do MobileAds (`ThisApp.kt`)
- Carregamento do banner (`MainActivity.kt`)
- Toggle "Enable Ads" nas configurações (layout + `SettingsFragment.kt` + `MainViewModel.kt` + `AppSettings.kt`)
- Dependência `play-services-ads` e o `meta-data` do `AndroidManifest.xml`
- **Bônus:** isso também removeu uma trava que faria o build falhar sem configuração
  extra — o `build.gradle` original exigia duas chaves (`ad.appid`, `ad.home.banner`)
  dentro de um `local.properties` que você teria que criar manualmente. Sem elas, o
  Gradle quebrava com `NullPointerException` antes mesmo de compilar. Isso não existe mais.

O diff completo de tudo isso está em `mudancas.diff`, na raiz do projeto.

## O que você ainda precisa fazer (não dá pra eu fazer por você)

### Criar seu próprio OAuth Client no Google Cloud Console
Isso é obrigatório desde a v1.3.1 do DriveStream original e é pessoal — fica vinculado
à sua conta Google, então precisa ser feito por você:

1. Acesse o [Google Cloud Console](https://console.cloud.google.com/)
2. Crie um projeto novo (ou use um existente)
3. Ative a **Google Drive API**
4. Em "Credenciais", crie um **OAuth Client ID**
5. Configure o Client ID e Client Secret dentro do próprio app, na tela de login
   (o app tem uma tela "Configure your drive client" pra isso)

Guia de referência (mencionado no próprio README do projeto original):
https://rclone.org/drive/#making-your-own-client-id

### Compilar
1. Abra a pasta `DriveStream/` no Android Studio
2. Deixe o Gradle sincronizar (vai baixar as dependências automaticamente)
3. Rode num emulador de Android TV ou instale o APK direto na sua TV (`adb install`)

### Firebase (opcional, não mexi nisso)
O projeto ainda usa Firebase Crashlytics/Analytics, que exige um `google-services.json`
na pasta `app/`. Se o Android Studio reclamar disso ao sincronizar, você tem duas opções:
- Gerar seu próprio `google-services.json` no [Firebase Console](https://console.firebase.google.com/) (grátis, leva 2 minutos)
- Ou me pedir pra remover o Crashlytics/Analytics também, se preferir não usar

## Próximos passos sugeridos (do GDD original)
- Testar com um episódio real (~1,5GB) pra validar o streaming via Range Requests
- Simplificar a navegação da UI (remover o que não interessa pro seu uso pessoal)
- Mapear um botão do controle remoto direto pro +90s (hoje é só clique na tela/D-pad)
