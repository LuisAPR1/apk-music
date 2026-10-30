# OLED Music

Versão 1.1: reconhece `app.revanced.android.apps.youtube.music` (pacote indicado para o teu ReVanced 8.40.54). Dá prioridade ao ReVanced instalado e ativo, mesmo que o YouTube Music original também esteja instalado. A mesma escolha é usada para abrir a app, controlar a sessão e obter a capa. Se o ReVanced não estiver instalado ou estiver desativado, usa o original. Não depende do número de versão 8.40.54.

Instala o novo APK por cima da versão anterior, escolhendo **Atualizar**. Mantém o mesmo identificador e a mesma chave de assinatura; não é necessário desinstalar.

Comando minimalista para o YouTube Music: fundo preto puro (#000000), capa grande com cantos arredondados e apenas anterior, reproduzir/pausar e seguinte. A barra de estado e a navegação ficam escondidas; desliza a partir da margem para as mostrar. A app acompanha a orientação do telemóvel.

## Instalar no Pixel

1. Copia `OLED-Music.apk` para o telemóvel e abre-o na app Ficheiros. Se o Android pedir, permite instalar apps dessa origem.
2. Abre **OLED Music** e toca em **Permitir acesso**. Ativa o acesso às notificações para OLED Music e regressa à app.
3. Se o Android bloquear esta opção com **Definição restrita**, abre **Definições → Apps → OLED Music → ⋮ → Permitir definições restritas**. Depois volta a conceder o acesso às notificações. Os nomes podem variar consoante o idioma/versão do Android.
4. Abre o YouTube Music, começa uma música e volta a OLED Music.

O Android concede acesso amplo às notificações. Este código filtra pelo pacote do YouTube Music antes de inspecionar notificações e não guarda nem transmite esse conteúdo. O acesso pode ser retirado nas definições do Android em qualquer altura.

## Comportamento

- Usa `MediaSessionManager`, `MediaController` e um `NotificationListenerService`, as APIs de sessão multimédia do Android. Não exige login Google nesta app.
- A música continua a ser reproduzida pelo YouTube Music. É necessário que a tua conta e o YouTube Music já permitam reprodução em segundo plano.
- Obtém a capa dos metadados da sessão, de um URI local acessível ou do ícone grande da notificação multimédia. Se o YouTube Music não disponibilizar uma imagem acessível, aparece um símbolo discreto.
- Os botões refletem as ações disponíveis na sessão. Por exemplo, seguinte pode estar desativado durante determinados conteúdos.
- Não há anúncios, analytics, permissão de Internet, serviço próprio de reprodução ou pedido para ignorar a otimização de bateria.
- Respeita o tempo normal até o ecrã se apagar. O preto reduz a área iluminada do OLED; a capa e os controlos continuam a acender píxeis.
- Android 8.0 ou posterior; compilado para API 36. A compatibilidade concreta com o Pixel 10 e a versão instalada do YouTube Music requer um teste nesse aparelho.

## Compilar novamente

O caminho usado para gerar este APK é `build.ps1`, sem dependências externas da app:

```powershell
.\build.ps1 -SdkRoot 'C:\caminho\Android\Sdk' -JavaHome 'C:\caminho\jdk'
```

Requer JDK 17 ou posterior, Android SDK Platform 36 e Build-Tools 36.0.0. O resultado fica em `build\OLED-Music.apk`. O script verifica a assinatura e o alinhamento do APK. Cada compilação cria uma pasta de trabalho nova.

A chave local `oledmusic-local.jks` é gerada para este projeto (senha de desenvolvimento `android`). Guarda essa chave para assinar futuras atualizações compatíveis com a instalação atual. Não está incluída no ZIP de código. Um APK assinado por outra chave exige desinstalar a versão anterior. Esta é uma distribuição pessoal, não uma publicação na Play Store.

Há também configuração de projeto Android Gradle para abrir o código no Android Studio; essa via não é a utilizada na compilação entregue. Usa AGP 8.13.0, Gradle 8.13 e JDK 17.

## Verificação no telemóvel

Depois de instalar, verificar: conceder acesso e voltar; mostrar capa enquanto toca; play/pausa; anterior/seguinte e mudança da capa; rodar o ecrã; sair/voltar com música em pausa; bloquear/desbloquear; retirar a permissão; fechar/reabrir o YouTube Music. As ações e a qualidade da capa dependem do que o YouTube Music publica na sessão.

## Referências

- [Android: sessões multimédia e acesso por listener de notificações](https://developer.android.com/reference/android/media/session/MediaSessionManager)
- [Android: definições restritas de apps instaladas por ficheiro](https://support.google.com/android/answer/12623953?hl=pt)

A imagem enviada serviu como referência de composição; a capa apresentada pela app é sempre a disponibilizada pela música em reprodução.
