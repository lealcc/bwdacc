# Rede local e Tailscale

Direção escolhida em 2026-11-01: bwdacc usa rede local em casa e Tailscale para
acesso remoto ao PC. Android 0.6.0-tailscale-dev e Desktop 0.4.0 implementam esse
fluxo. Cada cliente usa sua própria conta/rede Tailscale. Não há rede compartilhada
do bwdacc, domínio público, token Cloudflare ou serviço nosso de retransmissão.

## Conectar

1. Instalar e conectar Tailscale no PC e no Android, na mesma conta pessoal.
   Desktop oferece o site oficial; Android oferece abrir o app ou baixar pelo site.
2. Abrir Desktop > Dispositivos. Atualizar redes se acabou de conectar Tailscale.
   Escolher **Tailscale · 100.x.x.x** e **Ativar conexão**.
3. Selecionar **Parear celular**. No Android, Ajustes > Conexão > Parear com
   Desktop. Ler QR ou colar código e confirmar o celular no PC.
4. Iniciar o motor comercial para gerar. PC, Desktop e Tailscale precisam estar
   ligados. Para uso só em casa, selecionar o endereço Wi-Fi/LAN no lugar do Tailscale.

Não abrir portas no roteador nem publicar ComfyUI. Não é necessário nó de saída
(exit node), encaminhamento de sub-rede ou Funnel. O app não modifica DNS, conta,
políticas, firewall ou rotas do Tailscale automaticamente. Se o Windows bloquear,
permitir apenas o gateway TCP 8190 no endereço escolhido, com escopo apropriado à
rede privada; nunca liberar a porta 8188/8189 publicamente.

## Pareamento e reconexão

Ativar salva apenas endereço escolhido e preferência de ativação em
`%LOCALAPPDATA%\bwdacc-commercial\private-connection.json`. Identidade e credenciais
continuam separadas, protegidas por DPAPI/Keystore. Ao reabrir Desktop, o listener
volta no mesmo endereço. Se a interface estiver ausente, espera ela voltar; não
migra para outra rede nem para um túnel público. Desativar salva a preferência
desligada e encerra as conexões. Fechar Desktop preserva a preferência, mas não
mantém servidor em segundo plano. Não foi criado serviço nem inicialização com Windows.

QR dura dois minutos para **adicionar um aparelho**. Credencial já pareada não
vence junto do QR. O IP Tailscale é estável enquanto a identidade do dispositivo
for preservada. Remover/recriar o dispositivo, trocar de endereço ou perder as
chaves pode exigir outro pareamento. DHCP da LAN também pode mudar o endereço.
Tailscale pode solicitar novo login conforme a validade dos seus dispositivos;
isso é separado do QR do bwdacc e suas políticas não foram alteradas pelo app.
Nesta versão, alternar LAN/Tailscale exige escolher endereço e parear novamente;
descoberta/migração automática entre os dois não está implementada.

Desktop só oferece IPv4 de interfaces ativas. Endereços `100.64.0.0/10` só são
oferecidos/aceitos para bind quando atribuídos ao adaptador Tailscale. Android
aceita essa faixa e RFC1918, mantendo validação estrita de URL, identidade TLS,
hostname e aprovação. A faixa CGNAT sozinha não autentica o PC. IPv6 e pools
Tailscale personalizados fora da faixa padrão ainda não são suportados.

## Privacidade

Tailscale tenta conexão direta e usa retransmissão cifrada quando necessário.
Nosso HTTPS/WSS autenticado permanece dentro dessa conexão. Tailscale recebe
metadados operacionais; isso não é anonimato. O bwdacc não recebe mídia num relay
próprio nesse fluxo. ComfyUI permanece em loopback; gateway escuta somente no
endereço escolhido e loopback, com as restrições de [CONNECTION.md](CONNECTION.md).

O cofre protege as cópias no celular. O PC ainda processa e guarda inputs/outputs
em claro; limpeza após entrega e biometria permanecem pendentes. Nuvem gerenciada
é uma etapa separada e exige confiar no provedor durante processamento.

## Diagnóstico

- Sem endereço Tailscale: conectar o cliente Windows e atualizar redes.
- Sem acesso: conferir ambos na mesma rede Tailscale, PC ligado, gateway ativo,
  permissões entre dispositivos e firewall restrito ao gateway.
- Internet do celular falhou: conferir nó de saída e configurações de DNS no
  Tailscale. Não é necessário enviar toda a internet pelo PC. Android só permite
  uma VPN ativa; outra VPN pode interromper Tailscale.
- Geração indisponível com conexão ativa: iniciar o motor comercial no Desktop.

## Migração do protótipo remoto

Cloudflare saiu da interface e da inicialização normal. Perfis públicos antigos
não são carregados nem reativados. Seus arquivos não são apagados automaticamente.
As classes e fixtures antigas permanecem para regressão/histórico; comandos de
teste explícitos ainda podem criar um túnel. Não fazem parte do fluxo do cliente.
O Android reconhece um pareamento antigo e orienta parear novamente por LAN/Tailscale.
Não há necessidade de configurar conta/domínio Cloudflare para o produto local.

## Validação da prévia

- Desktop: 47 verificações passaram, incluindo sete novas de rede privada e a
  regressão das implementações anteriores. O teste local abriu HTTPS no adaptador
  Tailscale real do PC e reutilizou a credencial após recriar controller e identidade.
- Android: 17 verificações passaram no emulador contra o IP Tailscale do PC,
  sem `adb reverse`: QR, pin, hostname, credencial cifrada, prompt, upload, mídia,
  WSS, reinício do gateway, revogação e corrupção. O emulador usa a rede virtual
  do host; isso não é teste de dois clientes Tailscale pela internet/4G.
- A regressão LAN passou em 16 verificações no emulador, com encaminhamento ADB
  apenas nessa fixture local. Não usa nem altera a instalação pessoal.
- A UI do APK real mostrou conexão privada Tailscale e fila. Tela Desktop e
  pareamento/ajuda Android foram abertos e inspecionados.
- Teste físico foi adiado a pedido do usuário. APK do celular e atalho Windows
  anteriores permanecem preservados. Atualizar apenas o pacote comercial quando
  for testar. Reinício completo do PC, mudança Wi-Fi/4G e vídeos longos ainda faltam.

## Fontes

- [Endereços estáveis](https://tailscale.com/docs/concepts/ip-and-dns-addresses)
- [Faixa IPv4 padrão](https://tailscale.com/docs/concepts/tailscale-ip-addresses)
- [Conexão direta e retransmissão](https://tailscale.com/docs/concepts/traffic-routing-through-tailscale)
- [Criptografia](https://tailscale.com/docs/concepts/tailscale-encryption)
- [Privacidade e metadados](https://tailscale.com/privacy-policy)
- [VPNs simultâneas](https://tailscale.com/docs/reference/faq/other-vpns)
- [Diagnóstico de internet](https://tailscale.com/docs/reference/troubleshooting/connectivity/connect-internet-failure)
