# OLED Music

Versão 1.6 de teste, reconstruída a partir do APK 1.5 fornecido pelo proprietário. O objetivo é manter o comportamento da 1.5, com `versionCode 7` e `versionName 1.6`. O histórico e o texto exato do código original não foram recuperados. A origem e as verificações estão em `releases/VERIFICACAO-v1.6.txt`.

Reconhece `app.revanced.android.apps.youtube.music` e dá prioridade ao ReVanced instalado e ativo, mesmo que o YouTube Music original também esteja instalado. A mesma escolha é usada para abrir a app, controlar a sessão e obter a capa. Se o ReVanced não estiver instalado ou estiver desativado, usa o original.

Instala o novo APK por cima da versão anterior, escolhendo **Atualizar**. Mantém o mesmo identificador e a mesma chave de assinatura; não é necessário desinstalar.

Comando para o YouTube Music com fundo preto puro (#000000), capa com cantos arredondados e controlos anterior, reproduzir/pausar e seguinte. A barra de estado e a navegação ficam escondidas; desliza a partir da margem para as mostrar. A app acompanha a orientação do telemóvel.

Toca com três dedos para abrir as definições (também disponíveis através de uma ação de acessibilidade). As definições permitem ajustar o tamanho e escurecimento da capa, cores e escurecimento dos controlos, gestos na capa, título e artista, tipos e tamanhos de letra, barras de brilho e volume, temporizador e apresentação sobre o ecrã bloqueado. Nas definições, toca em **Guardar** para aplicar as alterações. Com gestos ativos, um duplo toque na capa alterna reprodução/pausa e deslizar na horizontal muda de faixa. Os controlos só podem ser ocultados com os gestos ativos.

## Instalar no Pixel

1. Copia `OLED-Music-v1.6.apk` para o telemóvel e abre-o na app Ficheiros. Escolhe **Atualizar** sobre a 1.5. Se o Android pedir, permite instalar apps dessa origem.
2. Abre **OLED Music** e toca em **Permitir acesso**. Ativa o acesso às notificações para OLED Music e regressa à app.
3. Se o Android bloquear esta opção com **Definição restrita**, abre **Definições → Apps → OLED Music → ⋮ → Permitir definições restritas**. Depois volta a conceder o acesso às notificações. Os nomes podem variar consoante o idioma/versão do Android.
4. Abre o YouTube Music, começa uma música e volta a OLED Music.

O Android concede acesso amplo às notificações. Este código filtra pelo pacote do YouTube Music antes de inspecionar notificações e não guarda nem transmite esse conteúdo. O acesso pode ser retirado nas definições do Android em qualquer altura.

## Comportamento

- Usa `MediaSessionManager`, `MediaController` e um `NotificationListenerService`, as APIs de sessão multimédia do Android. Não exige login Google nesta app.
- A música continua a ser reproduzida pelo YouTube Music. É necessário que a tua conta e o YouTube Music já permitam reprodução em segundo plano.
- Obtém a capa dos metadados da sessão, de um URI local acessível ou do ícone grande da notificação multimédia. Se o YouTube Music não disponibilizar uma imagem acessível, aparece um símbolo discreto.
- Os botões refletem as ações disponíveis na sessão. Por exemplo, seguinte pode estar desativado durante determinados conteúdos.
- O brilho usa autorização opcional para modificar definições do sistema. Ao ajustar a barra, muda para brilho manual. A barra de volume controla o volume multimédia.
- O temporizador usa um alarme do Android para pausar a sessão escolhida. Sem autorização para alarmes exatos, o momento de execução pode ser aproximado.
- A opção de leitor sobre o bloqueio mantém o bloqueio do Android; abrir as definições ou outra app pede desbloqueio.
- Não há anúncios, analytics, permissão de Internet, serviço próprio de reprodução ou pedido para ignorar a otimização de bateria.
- Respeita o tempo normal até o ecrã se apagar. O preto reduz a área iluminada do OLED; a capa e os controlos continuam a acender píxeis.
- Android 8.0 ou posterior; compilado para API 36. A compatibilidade concreta com o Pixel 10 e a versão instalada do YouTube Music requer um teste nesse aparelho.

## Compilar novamente

O APK 1.6 foi gerado em Linux com `build.py`, Android SDK Platform 36, Build Tools 36.0.0, Java 21 e compilador ECJ 3.38.0:

```sh
python3 build.py --sdk-root /caminho/Android/Sdk --ecj /caminho/ecj-3.38.0.jar
```

Com um JDK que inclua `javac`, não é necessário ECJ:

```sh
python3 build.py --sdk-root /caminho/Android/Sdk
```

O resultado fica em `build/OLED-Music-v1.6.apk`. A versão é lida de `app/build.gradle`. Não são reutilizados o DEX nem os recursos compilados do APK original: o código e os recursos do projeto são compilados novamente.

Em Windows existe também `build.ps1` (atualizado, mas não executado nesta recuperação):

```powershell
.\build.ps1 -SdkRoot 'C:\caminho\Android\Sdk' -JavaHome 'C:\caminho\jdk'
```

Requer JDK 17 ou posterior, Android SDK Platform 36 e Build Tools 36.0.0. O resultado fica em `build\OLED-Music-v1.6.apk`. Os scripts verificam a assinatura e o alinhamento do APK. Cada compilação cria uma pasta de trabalho nova.

A chave original `oledmusic-local.jks` já está no repositório por indicação do proprietário (senha de desenvolvimento `android`). A assinatura foi comparada com a do APK 1.5 e coincide. Os scripts exigem esta chave e não criam outra automaticamente. Guarda-a para assinar futuras atualizações compatíveis com a instalação atual. Não está incluída no ZIP de código. Esta é uma distribuição pessoal, não uma publicação na Play Store.

Há também configuração de projeto Android Gradle para abrir o código no Android Studio; essa via não é a utilizada na compilação entregue. Usa AGP 8.13.0, Gradle 8.13 e JDK 17.

## Verificação no telemóvel

Esta reconstrução foi compilada e verificada estruturalmente, sem teste num dispositivo ou emulador. Depois de atualizar sobre a 1.5, comparar: conservação das definições; aparência em ambas as orientações; título e artista; cores e paleta; toque com três dedos; duplo toque e deslizar na capa; play/pausa e anterior/seguinte; barras de brilho e volume; temporizador; ecrã bloqueado e autenticação nas definições; sair/voltar com música em pausa; retirar e conceder novamente a permissão. As ações e a qualidade da capa dependem do que o YouTube Music publica na sessão.

## Referências

- [Android: sessões multimédia e acesso por listener de notificações](https://developer.android.com/reference/android/media/session/MediaSessionManager)
- [Android: definições restritas de apps instaladas por ficheiro](https://support.google.com/android/answer/12623953?hl=pt)

A imagem enviada serviu como referência de composição; a capa apresentada pela app é sempre a disponibilizada pela música em reprodução.
