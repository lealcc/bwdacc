# Composições e seleção de objetos

Implementado na versão comercial Android `0.8.1-dev` e Desktop `0.5.1`.
O extrator continua compatível com Android `0.8.0-dev`; `0.8.1-dev` melhora os
rótulos sobre a imagem para acomodar descrições longas sem cobrir os outros boxes.
A versão pessoal não é alterada por este recurso.

## Preparação

1. Atualize o bwdacc Desktop e use a instalação/atualização do motor gerenciado.
2. Em Modelos, baixe **Compositor e seleção visual** (936.949.762 bytes, cerca de
   937 MB). Inclui Florence-2, EfficientSAM3, tokenizer, configurações e licença.
   O download confere o SHA-256 de cada arquivo antes de disponibilizá-lo.
3. Conecte o aplicativo ao Desktop pela conexão privada já pareada.

O catálogo oferece esse modelo auxiliar em todos os perfis de memória. A análise
usa o mesmo ComfyUI e a mesma fila das gerações; não abre um serviço adicional.
Uma geração em execução pode atrasar a análise.

## Descrições automáticas dos Fragmentos (0.11.4-dev)

A descrição complementar usa instruções específicas por categoria. Personagens
registram traços físicos visíveis, sem roupa, acessórios, expressão, pose ou cenário;
objetos registram forma, material e detalhes próprios; estilos descrevem somente
o tratamento visual; locais descrevem a estrutura do ambiente. O texto manual do
usuário permanece separado e não é substituído. Para atualizar uma descrição
antiga, abra o Fragmento e toque em **Atualizar descrição**.

Quando o servidor oferece BiRefNet e `BwdaccReferenceFocus`, personagens e objetos
passam por segmentação, fundo cinza e recorte com margem antes da VLM. Pixels
transparentes não são revelados; uma máscara vazia preserva o conteúdo visível.
O arquivo original, a capa e as imagens enviadas como referências de geração não
mudam. Estilos e locais usam a imagem completa. Servidores sem esses componentes
continuam usando as novas instruções com a imagem original. A segmentação ajuda
a isolar o primeiro plano; não identifica automaticamente qual pessoa o usuário
pretende escolher em uma foto com várias pessoas.

Para um ComfyUI existente, `tools/install_existing_references.py` instala o pacote
separado `bwdacc_reference_tools`, reutilizando os modelos visuais já baixados e
verificando seus checksums. Informe `--comfy`, `--models` (diretório de modelos do
Studio) e `--vendor` (vendor do pacote `momentos_workflows` do Studio). O script
não substitui nodes anteriores, não altera dependências Python e não reinicia o
servidor; o ambiente precisa oferecer as dependências de inferência do SAM,
incluindo `pycocotools`. O motor gerenciado inclui o novo node em sua instalação.

## Extrair uma composição

Abra o menu de uma imagem no feed, na galeria ou no visualizador e escolha
**Extrair composição**. Também é possível criar uma referência em
**+ > Referências > Composições** e escolher Imagens ou Biblioteca.

O extrator sugere até 12 boxes com posições e dimensões normalizadas. Revise a
detecção no editor: os boxes podem ser movidos, redimensionados, removidos ou
adicionados; cada um tem sua própria descrição. A proporção original permanece
no editor. Florence-2 usa `DENSE_REGION_CAPTION` para obter os boxes e suas
descrições em inglês. Quando a resposta contém apenas uma categoria curta, o
mesmo VLM descreve o recorte daquele box com `CAPTION`. As coordenadas extraídas
não mudam nessa segunda etapa. Nem todos os elementos são detectados, e as
descrições podem conter erros; ambos continuam editáveis. Resultados fora dos
limites, vazios ou duplicados são filtrados. Acima de 12 boxes, os maiores têm
prioridade; não são inventadas pontuações de confiança.

Salve com um nome e use **Usar composição**. A composição é copiada para o
rascunho do workflow atual, onde ainda pode ser alterada sem modificar a
referência salva. Workflows sem suporte a boxes preservam a referência, mas não
recebem condicionamento regional. A imagem usada como capa não é enviada como
referência visual de geração automaticamente.

## Selecionar um objeto ou personagem

No menu da imagem, escolha **Selecionar objeto**. Toque no elemento desejado.
**Incluir**, **Excluir área** e desfazer permitem refinar a seleção, com até
16 pontos por análise. A máscara deve respeitar os pontos positivos e negativos;
uma seleção incompatível é rejeitada, em vez de salvar outro objeto.

O recorte é salvo como PNG com transparência, limitado aos pixels selecionados.
Escolha uma referência nova ou existente de Objetos, Personagens, Estilos ou
Locais. O limite existente de três imagens por referência continua valendo.
Objetos usam a tag `&nome`; as permissões de texto, imagens, LoRAs e RefMod
continuam dependendo do workflow e da escolha do usuário.

Referências privadas mantêm sua classificação, capa protegida e restrições de
uso. O extrator exige revelar a imagem explicitamente, esconde novamente ao sair
do aplicativo e bloqueia capturas da tela. Os recortes passam pelo cofre
criptografado e são incluídos no backup portátil. Como nas outras operações
ComfyUI, a imagem é enviada ao computador selecionado; o input e o histórico da
análise podem permanecer no servidor. Isso não significa armazenamento exclusivo
no celular ou limpeza automática do servidor.

## Modelos e instalação reproduzível

A composição usa [Florence-2-base-ft](https://huggingface.co/microsoft/Florence-2-base-ft),
com 230 milhões de parâmetros. O checkpoint convertido de
`florence-community/Florence-2-base-ft` segue o carregamento nativo indicado na
[documentação do Transformers](https://huggingface.co/docs/transformers/model_doc/florence2).
Configuração, tokenizer e pesos são verificados antes do carregamento. O motor usa
`local_files_only=True`, `trust_remote_code=False` e pesos Safetensors; não baixa
código ou modelos durante a análise. SAM não é usado como fallback silencioso do
compositor se Florence estiver ausente.

Foi escolhido [EfficientSAM3 EV-M](https://github.com/SimonZeng7108/efficientsam3),
um modelo leve para texto e pontos em imagens. A versão oficial
[SAM 3.1](https://github.com/facebookresearch/sam3/blob/main/RELEASE_SAM3p1.md)
prioriza melhorias de vídeo; esta implementação não oferece segmentação temporal.

Código, checkpoints e dependências são fixados em `runtime.json`,
`visual-models.json`, `florence_manifest.json` e `requirements.lock`. Os arquivos
ficam em `models/vision/florence-2-base-ft` e `models/segmentation`, dentro do
ambiente comercial. O instalador reduz a importação do
pacote EfficientViT ao backbone necessário para inferência, evitando dependências
de treinamento/autoencoder que o aplicativo não usa. GPU sem BF16 usa precisão
normal; sem CUDA, o processamento usa CPU e pode ser significativamente mais lento.
Florence e SAM executam em etapas separadas e retornam para RAM após cada tarefa.

`bwdacc-desktop.exe --download-visual-resources <runtime> <report>` prepara uma
pasta vazia ou usa uma instalação gerenciada existente e baixa/verifica esse
pacote. Uma pasta não gerenciada com conteúdo é recusada. O comando não instala
modelos de geração nem inicia o ComfyUI.

## Validação

- Execute `tests/test_visual_extract.py` com o Python do motor: geometria,
  descrições, pontos positivos/negativos, deduplicação, limites, integridade dos
  recursos, correspondência dos manifestos e serialização da máscara.
- `bwdacc-desktop.exe --test-visual-runtime <runtime> <fixture-png> <report>`:
  instala-se previamente um runtime de teste isolado e o modelo; o comando inicia
  somente esse motor, executa composição e seleção reais e encerra seu processo.
- `tests/android-visual/run.ps1`: exige emulador; usa pacote descartável
  `com.leal.bwdacc.visualtest`, imagens sintéticas e as respostas gravadas pelo
  teste anterior. Verifica recorte, transparência, cofre, tags, persistência,
  permissões e caminhos de backup. Não usa dados nem instala no telefone pessoal.

O teste visual retira `FLAG_SECURE` apenas de diálogos com imagens sintéticas no
pacote de teste, depois de verificar que a proteção estava ativa. O APK comercial
mantém a proteção. No teste completo pela fila do motor gerenciado, a composição
levou 7,8 s e a seleção com SAM levou 7,4 s na imagem sintética de teste. Instalação,
validação de nodes e reinício imediato também passaram.
O primeiro teste isolado de Florence, incluindo descrições dos recortes, levou
cerca de 9,4 s e 573 MiB de VRAM alocada no RTX 3090. Esses valores não representam
garantia de latência ou memória para outras imagens e equipamentos.
