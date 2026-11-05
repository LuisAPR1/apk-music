# OLED Music

Versão 1.9: seletor Capa/Vídeo, letras sincronizadas e 19 famílias tipográficas, mantendo SoundCloud, seleção do último player e borda automática. Baseada na 1.6 recuperada do APK 1.5; a origem dessa recuperação está em `releases/VERIFICACAO-v1.6.txt`.

Players reconhecidos:

- SoundCloud: `com.soundcloud.android` (versão indicada pelo proprietário: `2025.05.27-release`).
- YouTube Music ReVanced: `app.revanced.android.apps.youtube.music`.
- YouTube Music original: `com.google.android.apps.youtube.music`.

A identificação usa os pacotes e as sessões multimédia Android, sem exigir um número específico de versão. Com as duas apps abertas, a que começa a reproduzir passa a ser a selecionada. Pausar, abrir a outra app sem dar play, mudar metadados ou atualizar notificações não muda a escolha. O último player continua selecionado em pausa e a sua identificação é guardada para a próxima abertura da OLED Music. O listener acompanha todas as sessões suportadas, mesmo quando o leitor da OLED Music não está visível. Antes de qualquer reprodução conhecida, o botão de abrir música usa uma app instalada (ReVanced, original ou SoundCloud).

Instala o novo APK por cima da versão anterior, escolhendo **Atualizar**. Mantém o mesmo identificador e a mesma chave de assinatura; não é necessário desinstalar.

Comando para SoundCloud e YouTube Music com fundo preto puro (#000000), capa com cantos arredondados e controlos anterior, reproduzir/pausar e seguinte. A barra de estado e a navegação ficam escondidas; desliza a partir da margem para as mostrar. A app acompanha a orientação do telemóvel.

Toca com três dedos para abrir as definições (também disponíveis através de uma ação de acessibilidade). As definições permitem ajustar o tamanho e escurecimento da capa, cores e escurecimento dos controlos, gestos na capa, título e artista, tipos e tamanhos de letra, barras de brilho e volume, temporizador e apresentação sobre o ecrã bloqueado. Nas definições, toca em **Guardar** para aplicar as alterações. Com gestos ativos, um duplo toque na capa alterna reprodução/pausa e deslizar na horizontal muda de faixa. Os controlos só podem ser ocultados com os gestos ativos.

Em **Definições → Capa**, a imagem preenche sempre o quadrado usando recorte central, com proporções preservadas. Podes ativar a borda, escolher a cor, ajustar a espessura entre 1 e 24 dp e o raio dos cantos entre 0 e 100 dp. A pré-visualização acompanha as alterações; toca em **Guardar** para aplicar. A borda vem desativada por defeito e os cantos mantêm os 12 dp da versão anterior. O raio é limitado a metade do lado da capa para se adaptar a capas pequenas.

Para usar a cor automática: em **Definições → Capa**, ativa **Mostrar borda na capa** e **Cor automática pela capa**, e toca em **Guardar**. A app agrupa tons semelhantes da capa visível e tenta a cor mais frequente que ocupe **até 40%** da imagem. Se ultrapassar 40% ou se confundir com as margens da capa, tenta a próxima por frequência. Exatamente 40% é permitido. A amostragem considera o quadrado após o recorte e os cantos arredondados; as margens originais e escurecidas são verificadas para evitar que a borda desapareça. Em capas monocromáticas ou sem cor elegível, usa a cor manual se contrastar, ou uma cor neutra com o melhor contraste possível. Em margens com muitas cores, nenhuma cor única pode garantir contraste perfeito em toda a volta.

O modo automático vem desligado por defeito. A cor manual mantém-se guardada e volta a ser usada ao desligar o toggle. A análise é feita em segundo plano numa amostra pequena; repetir play/pausa não volta a calcular a mesma capa. A percentagem é estimada por amostragem e agrupamento de tons, não por contagem exata dos píxeis da imagem original.

## Vídeo, letras e tipografia

As novas opções vêm desligadas por defeito para conservar a configuração existente.

**Definições → Vídeo → Mostrar seletor Capa / Vídeo** acrescenta um seletor em forma de cápsula acima da capa. O modo Vídeo usa o leitor oficial YouTube incorporado, com reprodução própria e ligação à Internet. Não aciona o seletor Música/Vídeo nativo do YouTube Music: o Android não disponibiliza um comando normalizado para isso. Ao abrir um vídeo identificado, pausa a app de música original. Ao voltar à capa, pode transferir a posição e retomar a música conforme a opção **Retomar música ao voltar à capa**. As versões áudio e vídeo podem ter introduções/durações diferentes.

O identificador disponível no YouTube Music é tentado primeiro; pode corresponder à versão áudio publicada no YouTube. Para escolher outro vídeo/um videoclip, usa **Ligar vídeo à música atual** nas definições e guarda um link YouTube. No SoundCloud, esta associação é necessária. Não há pesquisa automática nem escolha de um vídeo semelhante por título. Alguns vídeos impedem a incorporação, são privados, exigem login ou não estão disponíveis na região: a app mostra a indisponibilidade. O leitor precisa de uma área de pelo menos 200×200 dp; aumenta o tamanho da capa ou a janela se necessário. Os controlos oficiais do vídeo mantêm-se visíveis; o toggle de controlos regula a fila de botões da OLED Music.

Abrir outra app ou apagar o ecrã interrompe o vídeo; o iframe é descarregado para evitar áudio oculto. Uma rotação conserva o vídeo e a posição em memória. Os botões anterior/seguinte regressam à sessão da app original e mudam a música. Reproduzir novamente na app original também regressa à capa. O modo Vídeo depende do Android System WebView.

**Definições → Letras → Mostrar letras abaixo da capa** acrescenta um painel com linha ativa em destaque e deslocamento automático. Há duas origens:

- **Online · LRCLIB**: consulta por título, artista, álbum e duração; estes metadados são enviados ao LRCLIB quando a opção está ativa. A disponibilidade varia por música. Resultados são guardados em cache privado limitado; texto sem tempos aparece como **Texto**, sem sincronização inventada, e faixas instrumentais/sem letra têm um estado próprio.
- **Ficheiro local · LRC**: importa um LRC/texto UTF-8 para a música identificada no cabeçalho das definições. É copiado para armazenamento privado e pode funcionar sem rede. Uma nova importação substitui a anterior para a mesma faixa.

As letras acompanham posição, pausa, velocidade e saltos de reprodução. Tocar numa linha procura esse ponto quando o player suporta seek. Arrastar suspende o seguimento automático; **Seguir** volta à linha atual. O ajuste de sincronização permite compensar até cinco segundos para cada lado; valores positivos atrasam as letras. Se o player não disponibilizar um relógio válido, a app conserva a última posição conhecida em vez de inventar avanço.

Nas letras podes configurar família, estilo, tamanho, cor ativa, alinhamento, espaço entre linhas, altura do painel e avanço por toque. O layout adapta-se a retrato/paisagem e ao espaço libertado quando os controlos ficam ocultos; em paisagem a letra fica ao lado da capa. As opções de título/artista continuam em **Faixa**, agora com as mesmas 19 famílias disponíveis. As cinco fontes incluídas são Quicksand, Oswald, Playfair Display, Dancing Script e Bebas Neue; as licenças OFL estão em `app/src/main/assets/fonts/licenses`.

## Instalar no Pixel

1. Copia `OLED-Music-v1.9.apk` para o telemóvel e abre-o na app Ficheiros. Escolhe **Atualizar** sobre a 1.8 (ou anterior). Se o Android pedir, permite instalar apps dessa origem.
2. Abre **OLED Music** e toca em **Permitir acesso**. Ativa o acesso às notificações para OLED Music e regressa à app.
3. Se o Android bloquear esta opção com **Definição restrita**, abre **Definições → Apps → OLED Music → ⋮ → Permitir definições restritas**. Depois volta a conceder o acesso às notificações. Os nomes podem variar consoante o idioma/versão do Android.
4. Abre o SoundCloud ou YouTube Music, começa uma música e volta a OLED Music.

O Android concede acesso amplo às notificações. Este código filtra pelos três pacotes suportados antes de inspecionar as notificações. Não guarda nem transmite o conteúdo das notificações; guarda apenas o identificador do último player para conservar a seleção. O acesso pode ser retirado nas definições do Android em qualquer altura.

## Comportamento

- Usa `MediaSessionManager`, `MediaController` e um `NotificationListenerService`, as APIs de sessão multimédia do Android. Não exige login Google nesta app.
- A música continua a ser reproduzida na app de origem, com as capacidades de reprodução em segundo plano dessa app e conta.
- Obtém a capa dos metadados da sessão, de um URI local acessível ou do ícone grande da notificação multimédia. Se o player não disponibilizar uma imagem acessível, aparece um símbolo discreto.
- Os botões refletem as ações disponíveis na sessão. Por exemplo, seguinte pode estar desativado durante determinados conteúdos.
- O brilho usa autorização opcional para modificar definições do sistema. Ao ajustar a barra, muda para brilho manual. A barra de volume controla o volume multimédia.
- O temporizador usa um alarme do Android para pausar a sessão escolhida. Sem autorização para alarmes exatos, o momento de execução pode ser aproximado.
- A opção de leitor sobre o bloqueio mantém o bloqueio do Android; abrir as definições ou outra app pede desbloqueio.
- A permissão de Internet serve as letras online e o leitor YouTube opcional. A app não inclui analytics nem reprodução de áudio própria em segundo plano. O leitor incorporado pode apresentar conteúdo/anúncios geridos pelo YouTube.
- Respeita o tempo normal até o ecrã se apagar. O preto reduz a área iluminada do OLED; a capa e os controlos continuam a acender píxeis.
- Android 8.0 ou posterior; compilado para API 36. A compatibilidade concreta com o Pixel 10 e as versões instaladas dos players requer um teste nesse aparelho.

## Compilar novamente

O APK 1.9 foi gerado em Linux com `build.py`, Android SDK Platform 36, Build Tools 36.0.0, Java 21 e compilador ECJ 3.38.0:

```sh
python3 build.py --sdk-root /caminho/Android/Sdk --ecj /caminho/ecj-3.38.0.jar
```

Com um JDK que inclua `javac`, não é necessário ECJ:

```sh
python3 build.py --sdk-root /caminho/Android/Sdk
```

O resultado fica em `build/OLED-Music-v1.9.apk`. A versão é lida de `app/build.gradle`. Não são reutilizados o DEX nem os recursos compilados do APK original: o código e os recursos do projeto são compilados novamente.

Em Windows existe também `build.ps1` (atualizado, mas não executado neste ambiente):

```powershell
.\build.ps1 -SdkRoot 'C:\caminho\Android\Sdk' -JavaHome 'C:\caminho\jdk'
```

Requer JDK 17 ou posterior, Android SDK Platform 36 e Build Tools 36.0.0. O resultado fica em `build\OLED-Music-v1.9.apk`. Os scripts verificam a assinatura e o alinhamento do APK. Cada compilação cria uma pasta de trabalho nova.

A chave original `oledmusic-local.jks` já está no repositório por indicação do proprietário (senha de desenvolvimento `android`). A assinatura foi comparada com a do APK 1.5 e coincide. Os scripts exigem esta chave e não criam outra automaticamente. Guarda-a para assinar futuras atualizações compatíveis com a instalação atual. Não está incluída no ZIP de código. Esta é uma distribuição pessoal, não uma publicação na Play Store.

Há também configuração de projeto Android Gradle para abrir o código no Android Studio; essa via não é a utilizada na compilação entregue. Usa AGP 8.13.0, Gradle 8.13 e JDK 17.

## Verificação no telemóvel

A compilação, assinatura e alinhamento do APK foram verificados. Os testes de seleção, paleta, LRC, relógio de reprodução, links de vídeo e layout responsivo correm na JVM:

```sh
python3 tests/run.py --ecj /caminho/ecj-3.38.0.jar
# Ou, com javac instalado:
python3 tests/run.py
```

Não foi executado num dispositivo ou emulador neste ambiente. Testar no telemóvel:

1. Atualizar sobre a 1.8 e confirmar que as definições existentes se mantêm.
2. Reproduzir no ReVanced; abrir o SoundCloud sem dar play: deve continuar no ReVanced.
3. Dar play no SoundCloud: deve mudar capa, metadados e destino dos botões para SoundCloud.
4. Pausar no SoundCloud e voltar a OLED Music: deve continuar no SoundCloud.
5. Dar play novamente no ReVanced: deve mudar para ReVanced, incluindo com OLED Music em segundo plano/ecrã apagado.
6. Com ambos em pausa, fechar e voltar a abrir OLED Music: deve manter o último player.
7. Verificar capa retangular sem deformação e sem margens vazias, em ambas as orientações e tamanhos da capa.
8. Ativar a borda, mudar cor, espessura e cantos; guardar, rodar e reabrir para confirmar os valores.
9. Ativar a cor automática, guardar e trocar de música: a borda deve acompanhar a nova capa. Confirmar capas monocromáticas e imagens com margens da mesma cor dominante. Desligar o toggle deve restaurar a cor manual.
10. Ativar letras e confirmar sincronização ao pausar, retomar e avançar/recuar; tocar numa linha; arrastar/Seguir; testar sem letra, texto simples e LRC importado. Comparar espaço com controlos mostrados/ocultos, fontes e rotação.
11. Ativar o seletor Capa/Vídeo, testar um link incorporável e um bloqueado, voltar à capa e confirmar posição/reprodução; rodar e sair da app para confirmar ausência de áudio oculto.
12. Confirmar gestos na capa, título/artista, brilho, volume, temporizador no player selecionado e desbloqueio para abrir definições.

As ações, metadados e qualidade da capa dependem do que cada player publica na sessão Android. A versão SoundCloud indicada pelo proprietário não foi instalada nem executada neste ambiente.

## Referências

- [YouTube Music: alternar música/vídeo na app original](https://support.google.com/youtubemusic/answer/6313574?hl=en)
- [YouTube IFrame API](https://developers.google.com/youtube/iframe_api_reference)
- [LRCLIB API](https://lrclib.net/docs.txt)
- [Android: sessões multimédia e acesso por listener de notificações](https://developer.android.com/reference/android/media/session/MediaSessionManager)
- [Android: definições restritas de apps instaladas por ficheiro](https://support.google.com/android/answer/12623953?hl=pt)

A imagem enviada serviu como referência de composição; a capa apresentada pela app é sempre a disponibilizada pela música em reprodução.
