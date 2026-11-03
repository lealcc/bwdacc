package com.leal.comfyremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.ClipData;
import android.database.Cursor;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK_WORKFLOW = 1;
    private static final int PICK_COVERS = 2;
    private static final int PICK_ANIMATION_IMAGE = 3;
    private static final int PICK_PROJECT_COVER=4;
    private static final int PICK_PRESET_IMAGES=5;
    private static final int PICK_LIPSYNC_AUDIO=6;
    private LipsyncSheet lipsyncSheet,pickingLipsync;
    private static final int PAIR_DESKTOP=6;
    private ReferencePreset.Store referenceStore;
    private ReferenceJobs referenceJobs;
    private ReferenceStudio referenceStudio;
    private ReferenceStudio.Pick pendingPresetPick;
    private int pendingPresetLimit;
    private static final int BG = 0xFF000000;
    private static final int CARD = 0xFF151617;
    private static final int FIELD = 0xFF25272D;
    private static final int TEXT = Color.rgb(250, 250, 250);
    private static final int MUTED = Color.rgb(156, 160, 168);
    private static final int ACCENT = 0xFFE8EAED;
    private static final int MINT = Color.rgb(112, 226, 194);
    private static final int BORDER = 0x32FFFFFF;
    private static final String[] RATIOS = {"1:1 (Square)", "2:3 (Portrait Photo)",
            "3:2 (Photo)", "3:4 (Portrait Standard)", "4:3 (Standard)",
            "9:16 (Portrait Widescreen)", "16:9 (Widescreen)", "21:9 (Ultrawide)"};
    private static final String[] DEFAULT_COVERS = {
            "covers/default/cover-1.png", "covers/default/cover-2.png",
            "covers/default/cover-3.png"};
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler();
    private final List<Field> fields = new ArrayList<>();
    private final List<Generation> generations = new ArrayList<>();
    private final android.util.LruCache<String,Bitmap> coverCache=new android.util.LruCache<String,Bitmap>(24*1024){
        @Override protected int sizeOf(String key,Bitmap bitmap){return Math.max(1,bitmap.getByteCount()/1024);}
    };
    private final List<Runnable> hideReveals = new ArrayList<>();
    private EditText serverInput;
    private TextView connection, workflowNameView, queue, result, coverCount, headerTitle, connectionMode;
    private ServerMonitor serverMonitor;
    private LinearLayout workflowDetails;
    private LinearLayout fieldList, advancedList, feedList, connectionPanel, createPage, feedPage;
    private Button runButton, advancedButton, sectionButton;
    private MotionLight motionLight;
    private ComposerFold mainFold;
    private float browseStartX,browseStartY;private boolean browsingContent;
    private ScrollView mainScroll;
    private JSONObject workflow;
    private volatile String baseUrl;
    private volatile boolean connected;
    private String workflowName = "Workflow";
    private volatile boolean polling;
    private final Map<String, Long> missingJobs = new HashMap<>();
    private final Map<String, Integer> downloadFailures = new HashMap<>();
    private int visibleCount = 20;
    private MasonryGallery gallery;
    private LinearLayout galleryPage;
    private MasonryGallery.Adapter galleryAdapter;
    private Button privacyButton;
    private boolean owlControlsUnlocked;
    private int versionTapCount;
    private long lastVersionTap;
    private LinearLayout privacyOptions;
    private Dialog privacyOptionsDialog;
    private volatile boolean hideAllNsfw;
    private boolean privateCoversEnabled;
    private final List<Generation> visibleGenerations = new ArrayList<>();
    private final List<Generation> gallerySets = new ArrayList<>();
    private ImageView revealedGridImage;
    private Bitmap revealedGridCover;
    private EditText dockPrompt;
    private Button workflowChip;
    private LinearLayout quickOptions;
    private int batchCount = 1;
    private ReferenceTray mainReferences;
    private Button dockLoraChip;
    private Button dockAgentChip;
    private boolean videoFirstLast, videoSubmitting;
    private int videoQuality=33, videoDuration=1;
    private String videoRatio="16:9";
    private Button loraButton;
    private final List<PromptStyle> locations = new ArrayList<>();
    private boolean editingLocations;
    private Dialog settingsDialog;
    private ProgressBar generationProgress;
    private final List<PromptStyle> promptStyles = new ArrayList<>();
    private String selectedStyleId = "";
    private Button styleButton;
    private Dialog stylesDialog;
    private LinearLayout styleList;
    private LinearLayout galleryActions, generationDock;
    private boolean selecting, bindingFields;
    private final java.util.Set<String> selectedGenerations = new java.util.HashSet<>();
    private final List<CharacterPreset> characters = new ArrayList<>();
    private String selectedCharacterId = "";
    private Button characterButton;
    private Dialog charactersDialog;
    private LinearLayout characterList;
    private final Runnable persistDraft = () -> saveDraft();
    private final java.util.Set<String> editUploads = new java.util.HashSet<>();
    private VersionViewer versionViewer;
    private int selectedTab;
    private LinearLayout prompterPage,prompterList,projectsPage,advancedSettings,memorySettings;
    private android.widget.FrameLayout fragmentsPage;private int fragmentReturnTab;
    private String activeProjectId="",pendingProjectCover="",prompterSession="";
    private final List<ProjectAlbum> projects=new ArrayList<>();
    private boolean galleryAutoplay=true,appResumed;
    private ComfyProgressSocket progressSocket;
    private String progressJob="";
    private int progressEpoch;
    private long progressRetryAt;
    private final List<GalleryVideoPreview> feedVideoPreviews=new ArrayList<>();
    private final Runnable expireTrials=new Runnable(){public void run(){cleanupExpiredTrials();handler.postDelayed(this,60_000);}};

    private static final class CharacterPreset {
        String id, name, description, coverId, sourceId;
        boolean privateCover;
        CharacterPreset(String id, String name, String description, String coverId, String sourceId, boolean privateCover) {
            this.id = id; this.name = name; this.description = description;
            this.coverId = coverId; this.sourceId = sourceId; this.privateCover = privateCover;
        }
    }

    private static final class PromptStyle {
        final String id, name, text;
        PromptStyle(String id, String name, String text) {
            this.id = id; this.name = name; this.text = text;
        }
    }

    private static final class Field {
        String nodeId, key, label, primaryLabel;
        Object original;
        EditText edit;
        Spinner spinner;
        android.widget.Switch toggle;
    }

    private static final class Generation extends MediaFamilies.Record {
        String workflowName, prompt, state, filename, subfolder, imageType, error;
        String promptTemplate = "";
        int mediaWidth,mediaHeight;
        String serverAddress = "";
        long createdAt;
        long generationStartedAt,generationFinishedAt;
        final GenerationProgress progress=new GenerationProgress();
        long progressDisplayAt;
        GenerationProgress.Display progressDisplay;
        boolean originalSizeKnown;
        long videoMillis;
        JSONObject settings=new JSONObject();
        boolean nsfw;
        boolean temporary;
        String trialSession="";
        long expiresAt;
        java.util.Set<String> projectIds=new java.util.LinkedHashSet<>();

        JSONObject toJson() throws Exception {
            JSONObject value = new JSONObject();
            value.put("id", id);
            value.put("workflowName", workflowName);
            value.put("prompt", prompt);
            value.put("promptTemplate",promptTemplate);
            value.put("state", state);
            value.put("filename", filename);
            value.put("subfolder", subfolder);
            value.put("imageType", imageType);
            value.put("error", error);
            value.put("createdAt", createdAt);
            value.put("observedStart",progress.observedStart);value.put("generationStartedAt",generationStartedAt);value.put("generationFinishedAt",generationFinishedAt);value.put("settings",settings);
            value.put("originalSizeKnown",originalSizeKnown);value.put("videoMillis",videoMillis);
            value.put("nsfw", nsfw);
            value.put("temporary",temporary);value.put("trialSession",trialSession);value.put("expiresAt",expiresAt);value.put("projectIds",new JSONArray(projectIds));
            value.put("rootId", rootId);
            value.put("parentId", parentId);
            value.put("editNumber", editNumber);
            value.put("mediaKind", mediaKind);
            value.put("serverAddress", serverAddress);
            value.put("sourceId",sourceId);value.put("familySchema",familySchema);
            value.put("mediaWidth",mediaWidth);value.put("mediaHeight",mediaHeight);
            return value;
        }

        static Generation fromJson(JSONObject value) {
            Generation item = new Generation();
            item.id = value.optString("id");
            item.workflowName = value.optString("workflowName", "Workflow");
            item.prompt = value.optString("prompt");
            item.promptTemplate=value.optString("promptTemplate", "");
            item.state = value.optString("state", "queued");
            item.filename = value.optString("filename");
            item.subfolder = value.optString("subfolder");
            item.imageType = value.optString("imageType", "output");
            item.error = value.optString("error");
            item.createdAt = value.optLong("createdAt", System.currentTimeMillis());
            item.progress.observedStart=value.optLong("observedStart",0);
            item.generationStartedAt=value.optLong("generationStartedAt",0);item.generationFinishedAt=value.optLong("generationFinishedAt",0);
            JSONObject settings=value.optJSONObject("settings");if(settings!=null)item.settings=settings;
            item.originalSizeKnown=value.optBoolean("originalSizeKnown",false);item.videoMillis=value.optLong("videoMillis",0);
            item.nsfw = value.optBoolean("nsfw", false);
            item.temporary=value.optBoolean("temporary",false);item.trialSession=value.optString("trialSession");item.expiresAt=value.optLong("expiresAt",0);
            JSONArray projectIds=value.optJSONArray("projectIds");if(projectIds!=null)for(int i=0;i<projectIds.length();i++)item.projectIds.add(projectIds.optString(i));
            item.rootId = value.optString("rootId", "");
            item.parentId = value.optString("parentId", "");
            item.editNumber = value.optInt("editNumber", 0);
            item.mediaKind = value.optString("mediaKind", "image");
            item.serverAddress = value.optString("serverAddress", "");
            item.sourceId=value.optString("sourceId","");item.familySchema=value.optInt("familySchema",0);
            item.mediaWidth=value.optInt("mediaWidth",0);item.mediaHeight=value.optInt("mediaHeight",0);
            return item;
        }
    }

    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(UiLanguage.wrap(base));}

    private boolean vaultUiReady;
    private HelpCenter helpCenter;
    boolean libraryReady(){return vaultUiReady;}
    @Override public android.content.SharedPreferences getPreferences(int mode){return VaultFiles.preferences();}
    private void showVaultGate(boolean failed){
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setGravity(Gravity.CENTER);panel.setPadding(48,48,48,48);panel.setBackgroundColor(Color.BLACK);
        TextView title=new TextView(this);title.setText(failed?UiLanguage.t("Cofre indisponível"):UiLanguage.t("Preparando seu cofre"));title.setTextSize(22);title.setTextColor(Color.WHITE);panel.addView(title);
        TextView detail=new TextView(this);detail.setText(failed?UiLanguage.t("Arquivos preservados. Não limpe os dados nem reinstale o app. Feche e tente abrir novamente."):UiLanguage.t("Protegendo mídia e configurações neste aparelho…"));detail.setTextSize(16);detail.setTextColor(0xffb8b8c2);detail.setGravity(Gravity.CENTER);detail.setPadding(0,28,0,0);panel.addView(detail);
        if(!failed)panel.addView(new android.widget.ProgressBar(this));setContentView(panel);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if(VaultMaintenance.restartRequired()){
            showVaultGate(true);new FlowDialogs.Builder(this).setTitle(UiLanguage.t("Reabrir o cofre")).setMessage(UiLanguage.t("Feche e abra o app para carregar a restauração."))
                .setCancelable(false).setPositiveButton(UiLanguage.t("Fechar app"),(d,w)->{finishAffinity();android.os.Process.killProcess(android.os.Process.myPid());}).show();return;
        }
        if(!VaultFiles.ready()){
            showVaultGate(false);
            new Thread(()->{try{VaultFiles.prepare(this);runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())recreate();});}
                catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())showVaultGate(true);});}},"vault-prepare").start();
            return;
        }
        vaultUiReady=true;UiLanguage.init(this);DesktopConnection.initialize(this);
        PrompterCleanupService.activity=new java.lang.ref.WeakReference<>(this);
        prompterSession=getPreferences(0).getString("prompter_session",UUID.randomUUID().toString());getPreferences(0).edit().putString("prompter_session",prompterSession).apply();
        galleryAutoplay=getPreferences(0).getBoolean("gallery_autoplay",true);loadProjects();PrompterCleanupService.schedule(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        videoFirstLast=getPreferences(0).getBoolean("main_video_first_last",false);videoQuality=getPreferences(0).getInt("main_video_quality",33);videoDuration=Math.max(0,Math.min(3,getPreferences(0).getInt("main_video_duration",1)));videoRatio=getPreferences(0).getString("main_video_ratio","16:9");
        batchCount = GenerationOptions.count(getPreferences(0).getInt("batch_count", 1));
        try{generationStorage=new GenerationStorage(getFilesDir());loadFeed();loadRestoredInputs();}catch(Exception error){vaultUiReady=false;showVaultGate(true);return;}
        worker.execute(()->{generationStorage.recoverCleanup();LipsyncAudio.cleanup(getFilesDir());});
        if(MediaFamilies.migrate(generations)&&!saveFeed())Toast.makeText(this,UiLanguage.t("Falha ao salvar a organização dos vídeos. O app tentará novamente ao abrir."),Toast.LENGTH_LONG).show();
        loadStyles();
        loadLocations();
        loadCharacters();
        initializeReferences();
        owlControlsUnlocked = getPreferences(0).getBoolean("owl_controls_unlocked", false);
        hideAllNsfw = !owlControlsUnlocked || getPreferences(0).getBoolean("hide_all_nsfw", true);
        if(!owlControlsUnlocked)getPreferences(0).edit().putBoolean("hide_all_nsfw",true).apply();
        Appearance.mode(this,!hideAllNsfw);motionLight=new MotionLight(this);
        try(InputStream input=getAssets().open("lora-privacy.json")){LoraPrivacy.load(new JSONObject(new String(read(input,1_000_000),StandardCharsets.UTF_8)));}catch(Exception ignored){}
        try{LoraPrivacy.loadNames(new JSONObject(getPreferences(0).getString(LoraPrivacy.NAMES_KEY,"{}")));}catch(Exception ignored){}
        if(hideAllNsfw)disablePrivateLoras();
        privateCoversEnabled = getPreferences(0).getBoolean("private_covers", true);

        LinearLayout root = column();
        root.setBackground(Appearance.atmosphere());
        setContentView(root);
        mainScroll = new ScrollView(this);
        mainScroll.setFillViewport(true);
        mainScroll.setBackgroundColor(Color.TRANSPARENT);
        mainScroll.setVerticalScrollBarEnabled(false);
        mainScroll.setClipToPadding(true);
        mainScroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->syncFeedPlayback());
        mainScroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->syncFeedPlayback());
        LinearLayout page = column();
        page.setPadding(dp(16), dp(4), dp(16), dp(20));
        mainScroll.addView(page);
        root.addView(mainScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark = text(UiLanguage.t("M"), 19, BG, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(ACCENT, 17, 0));
        LinearLayout titles = column();
        titles.setPadding(0, 0, 0, 0);
        headerTitle = text(UiLanguage.t("bwdacc"), 25, TEXT, true);Branding.apply(headerTitle);
        titles.addView(headerTitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        connection = text(UiLanguage.t("● Offline"), 12, MUTED, true);
        connection.setGravity(Gravity.CENTER);
        connection.setPadding(dp(10), dp(7), dp(10), dp(7));
        connection.setBackground(round(FIELD, 20, 0));
        privacyButton = button("", FIELD, TEXT);
        privacyButton.setTextSize(11);
        header.addView(privacyButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        Button settings=button("",Color.TRANSPARENT,TEXT);FlowDesign.symbol(settings,"settings",UiLanguage.t("Ajustes"));settings.setOnClickListener(v->openSettings());header.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));
        sectionButton=button(UiLanguage.t("Feed"),FIELD,TEXT);sectionButton.setTextSize(12);sectionButton.setSingleLine(true);sectionButton.setMaxWidth(dp(120));sectionButton.setPadding(dp(10),0,dp(8),0);sectionButton.setCompoundDrawables(null,null,FlowDesign.icon(this,"down",15),null);sectionButton.setCompoundDrawablePadding(dp(5));sectionButton.setOnClickListener(v->showNavigation());header.addView(sectionButton,new LinearLayout.LayoutParams(-2,dp(48)));
        updatePrivacyButton();
        privacyButton.setOnClickListener(v -> toggleNsfwVisibility());
        root.addView(header, 0);
        gap(page, 0);

        createPage = column();
        page.addView(createPage);
        LinearLayout workflowCard = card();
        createPage.addView(workflowCard);
        TextView overline = text(UiLanguage.t("WORKFLOW"), 10, MUTED, true);
        overline.setLetterSpacing(.14f);
        workflowCard.addView(overline);
        LinearLayout workflowRow = row();
        workflowRow.setGravity(Gravity.CENTER_VERTICAL);
        workflowNameView = text(UiLanguage.t("Nenhum workflow"), 19, TEXT, true);
        workflowNameView.setSingleLine(true);
        workflowNameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        workflowRow.addView(workflowNameView, new LinearLayout.LayoutParams(0, -2, 1));
        Button importButton = button(UiLanguage.t("Trocar"), FIELD, TEXT);
        workflowRow.addView(importButton);
        workflowCard.addView(workflowRow);
        Button workflowOptions = button(UiLanguage.t("Opções do workflow"), Color.TRANSPARENT, MUTED);
        workflowCard.addView(workflowOptions);
        workflowDetails = column();
        workflowDetails.addView(connection);
        workflowDetails.setVisibility(View.GONE);
        workflowCard.addView(workflowDetails);
        workflowOptions.setOnClickListener(v -> workflowDetails.setVisibility(
                workflowDetails.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        gap(workflowDetails, 8);
        coverCount = text(UiLanguage.t("3 capas padrão de reserva"), 12, MUTED, false);
        workflowDetails.addView(coverCount);
        gap(workflowDetails, 7);
        Button pickCovers = button(UiLanguage.t("Personalizar capas"), FIELD, TEXT);
        workflowDetails.addView(pickCovers);
        pickCovers.setOnClickListener(v -> {
            if (workflow == null) {
                result.setText(UiLanguage.t("Importe um workflow antes de escolher capas."));
                return;
            }
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, PICK_COVERS);
        });
        gap(workflowDetails, 7);
        Button serverToggle = button(UiLanguage.t("Servidor e conexão"), FIELD, MUTED);
        workflowDetails.addView(serverToggle);
        connectionPanel = column();
        connectionPanel.setVisibility(View.GONE);
        connectionPanel.addView(text(UiLanguage.t("Desktop pareado"), 13, MUTED, false));
        serverInput = new EditText(this);
        styleInput(serverInput);
        serverInput.setSingleLine(true);
        serverInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        serverInput.setHint(UiLanguage.t("Pareie com seu computador"));
        serverInput.setText(DesktopConnection.endpoint(this));serverInput.setKeyListener(null);
        // The origin is an internal transport identity, not an editable product setting.
        serverInput.setVisibility(View.GONE);connectionPanel.addView(serverInput);
        connectionMode=text(DesktopConnection.description(this),15,TEXT,true);connectionPanel.addView(connectionMode);
        Button pairButton=button(UiLanguage.t("Parear com Desktop"),ACCENT,BG);FlowDesign.labelIcon(pairButton,"qr");pairButton.getCompoundDrawables()[0].setColorFilter(BG,android.graphics.PorterDuff.Mode.SRC_IN);connectionPanel.addView(pairButton);
        pairButton.setOnClickListener(v->startActivityForResult(new Intent(this,PairingActivity.class),PAIR_DESKTOP));
        Button tailscaleButton=button(UiLanguage.t("Tailscale · acesso fora de casa"),FIELD,TEXT);connectionPanel.addView(tailscaleButton);
        FlowDesign.labelIcon(tailscaleButton,"connection");tailscaleButton.setOnClickListener(v->ConnectionGuide.show(this));
        Button forgetDesktop=button(UiLanguage.t("Esquecer pareamento"),FIELD,MUTED);connectionPanel.addView(forgetDesktop);
        forgetDesktop.setVisibility(DesktopConnection.endpoint(this).isEmpty()?View.GONE:View.VISIBLE);
        forgetDesktop.setOnClickListener(v->new FlowDialogs.Builder(this).setTitle(UiLanguage.t("Esquecer este Desktop?")).setMessage(UiLanguage.t("Gerações salvas permanecem. Remova também este celular na lista Dispositivos do Desktop para revogar o acesso.")).setNegativeButton(UiLanguage.t("Cancelar"),null).setPositiveButton(UiLanguage.t("Esquecer"),(d,w)->{stopProgressSocket();DesktopConnection.forget(this);connected=false;polling=false;baseUrl=null;serverInput.setText("");connectionMode.setText(DesktopConnection.description(this));getPreferences(0).edit().remove("server").apply();connection.setText(UiLanguage.t("Desktop não pareado"));}).show());
        Button connectButton = button(UiLanguage.t("Conectar"), ACCENT, BG);
        connectButton.setVisibility(DesktopConnection.endpoint(this).isEmpty()?View.GONE:View.VISIBLE);
        connectionPanel.addView(connectButton);
        workflowDetails.addView(connectionPanel);
        serverToggle.setOnClickListener(v -> connectionPanel.setVisibility(
                connectionPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        connectButton.setOnClickListener(v -> connect());
        importButton.setOnClickListener(v -> chooseWorkflow());
        gap(createPage, 8);
        LinearLayout privacySettings = card();
        android.widget.Switch coversSetting = new android.widget.Switch(this);
        coversSetting.setText(UiLanguage.t("Usar capas falsas")); coversSetting.setTextColor(TEXT);
        coversSetting.setChecked(privateCoversEnabled);
        privacySettings.addView(coversSetting, new LinearLayout.LayoutParams(-1, dp(48)));
        privacySettings.addView(text(UiLanguage.t("Prioriza gerações SFW da galeria, na proporção do original. Capas pré-configuradas são a reserva. A coruja na tela inicial controla o modo privado."), 12, MUTED, false));
        coversSetting.setOnCheckedChangeListener((control, checked) -> {
            if(versionViewer!=null){versionViewer.cancelMotion();versionViewer.conceal();versionViewer.stopVideo();}
            privateCoversEnabled = checked;
            getPreferences(0).edit().putBoolean("private_covers", checked).apply();
            renderFeed();if(mainReferences!=null)mainReferences.refresh();if(versionViewer!=null)versionViewer.references.refresh();
        });
        createPage.addView(privacySettings);
        gap(createPage, 8);

        LinearLayout composer = card();
        createPage.addView(composer);
        composer.addView(text(UiLanguage.t("Imagem"), 16, TEXT, true));
        gap(composer, 4);
        fieldList = column();
        composer.addView(fieldList);
        runButton = button(UiLanguage.t("Gerar imagem"), ACCENT, BG);
        runButton.setTextSize(16);
        composer.addView(runButton, new LinearLayout.LayoutParams(-1, dp(54)));
        result = text("", 13, MUTED, false);
        composer.addView(result);
        queue = text("", 12, MUTED, false);
        composer.addView(queue);
        advancedButton = button(UiLanguage.t("Ajustes técnicos"), FIELD, MUTED);
        composer.addView(advancedButton);
        advancedList = column();
        advancedList.setVisibility(View.GONE);
        composer.addView(advancedList);
        advancedButton.setOnClickListener(v -> {
            boolean show = advancedList.getVisibility() != View.VISIBLE;
            advancedList.setVisibility(show ? View.VISIBLE : View.GONE);
            advancedButton.setText(show ? UiLanguage.t("Ocultar ajustes técnicos") : UiLanguage.t("Ajustes técnicos"));
        });
        runButton.setOnClickListener(v -> runWorkflow());
        feedPage = column();
        page.addView(feedPage);
        gap(feedPage, 2);

        feedList = column();
        feedPage.addView(feedList);
        prompterPage=column();page.addView(prompterPage);prompterList=column();
        projectsPage=column();page.addView(projectsPage);

        gallery = new MasonryGallery(this);
        galleryAdapter = new MasonryGallery.Adapter() {
            public double aspect(int position){return galleryAspect(gallerySets.get(position));}
            public String key(int position){return familyId(gallerySets.get(position));}
            public int getCount() { return gallerySets.size(); }
            public Object getItem(int position) { return gallerySets.get(position); }
            public long getItemId(int position) { return position; }
            boolean video(int position){Generation item=gallerySets.get(position);return !selecting&&"video".equals(item.mediaKind)&&!covered(item)&&"done".equals(item.state);}
            void playback(View view,boolean active){if(view.getTag() instanceof GalleryVideoPreview)((GalleryVideoPreview)view.getTag()).active(active&&galleryAutoplay&&appResumed);}
            public View getView(int position, View recycled, android.view.ViewGroup parent) {
                return galleryTile(gallerySets.get(position));
            }
        };
        gallery.setAdapter(galleryAdapter);
        galleryPage = column();
        galleryActions = row();
        galleryActions.setPadding(dp(12), dp(4), dp(12), dp(4));
        galleryPage.addView(galleryActions);
        updateGalleryActions();
        galleryPage.addView(gallery, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView galleryEmpty = text(UiLanguage.t("Sua galeria começa aqui\nCrie uma imagem para adicionar à coleção."), 15, MUTED, false);
        galleryEmpty.setGravity(Gravity.CENTER);
        galleryEmpty.setPadding(dp(24), 0, dp(24), 0);
        galleryPage.addView(galleryEmpty, new LinearLayout.LayoutParams(-1, 0, 1));
        gallery.setEmptyView(galleryEmpty);
        root.addView(galleryPage, new LinearLayout.LayoutParams(-1, 0, 1));
        fragmentsPage=new android.widget.FrameLayout(this);fragmentsPage.setVisibility(View.GONE);root.addView(fragmentsPage,new LinearLayout.LayoutParams(-1,0,1));
        page.removeView(createPage);
        createPage.setVisibility(View.VISIBLE);
        LinearLayout dock = column();
        generationDock = dock;
        FlowDesign.surface(dock);
        styleButton = button("", Color.TRANSPARENT, MUTED);
        styleButton.setTextSize(12);
        styleButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        styleButton.setSingleLine(true);
        styleButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
        styleButton.setPadding(dp(2), 0, dp(2), 0);
        styleButton.setOnClickListener(v -> openStyles());
        updateStyleButton();
        LinearLayout presets = row();
        presets.addView(styleButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        characterButton = button("", Color.TRANSPARENT, MUTED);
        characterButton.setTextSize(12);
        characterButton.setSingleLine(true);
        characterButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
        characterButton.setPadding(dp(6), 0, 0, 0);
        characterButton.setOnClickListener(v -> openCharacters());
        updateCharacterButton();
        presets.addView(characterButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button referencesButton=button(UiLanguage.t("Fragmentos"),Color.TRANSPARENT,TEXT);FlowDesign.labelIcon(referencesButton,"layers");referencesButton.setOnClickListener(v->openReferences(0));workflowCard.addView(referencesButton);
        Button locationButton=button(UiLanguage.t("Locais · #"),Color.TRANSPARENT,MUTED);FlowDesign.labelIcon(locationButton,"location");
        locationButton.setOnClickListener(v->openLocations());
        dockPrompt = new TagInput();
        dockPrompt.setHint(UiLanguage.t("Descreva sua imagem…"));
        dockPrompt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        FlowDesign.prompt(dockPrompt);
        mainReferences=new ReferenceTray(null);dock.addView(mainReferences);
        dock.addView(dockPrompt, new LinearLayout.LayoutParams(-1, -2));
        attachMentions(dockPrompt,dock);
        Button add = button("+", Color.TRANSPARENT, TEXT);
        add.setTextSize(25);
        add.setContentDescription(UiLanguage.t("Adicionar referência e tags"));
        add.setOnClickListener(v -> showAddMenu(add));

        workflowChip = new FlowDesign.Chip(this);
        workflowChip.setOnClickListener(v -> { hideKeyboard(dockPrompt); rebuildQuickOptions(); UiMotion.toggle(quickOptions); });
        composer.removeView(runButton);
        runButton.setContentDescription(UiLanguage.t("Gerar imagem"));
        dockLoraChip=loraChip(null);dockAgentChip=agentChip(null);dock.addView(FlowDesign.actions(this,add,dockLoraChip,dockAgentChip,workflowChip,runButton));
        generationProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        generationProgress.setIndeterminate(true);
        generationProgress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        generationProgress.setVisibility(View.GONE);
        root.addView(generationProgress, new LinearLayout.LayoutParams(-1, dp(2)));
        composer.removeView(result);
        result.setTextSize(11);
        result.setMaxLines(2);
        result.setPadding(dp(20),dp(2),dp(20),dp(6));result.setGravity(Gravity.CENTER);result.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        collapseEmpty(result);
        result.addTextChangedListener(new android.text.TextWatcher(){
            Runnable hide=()->result.setVisibility(View.GONE);
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void afterTextChanged(android.text.Editable e){}
            public void onTextChanged(CharSequence s,int start,int before,int count){handler.removeCallbacks(hide);String source=UiLanguage.original(s.toString()).toLowerCase(Locale.ROOT);if(source.matches(".*(conexão restabelecida|workflow selecionado|ajustes salvos|prompt reutilizado).*"))handler.postDelayed(hide,8000);}
        });
        LinearLayout.LayoutParams dockParams = new LinearLayout.LayoutParams(-1, -2);
        dockParams.setMargins(dp(8), dp(8), dp(8), dp(8));
        root.addView(dock, dockParams);
        quickOptions=column();UiMotion.hide(quickOptions);LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,-2);qp.setMargins(dp(8),0,dp(8),dp(8));root.addView(quickOptions,qp);root.addView(result);
        mainFold=new ComposerFold(dock,dockPrompt,UiLanguage.t("Escrever prompt"));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                android.graphics.Insets ime = insets.getInsets(android.view.WindowInsets.Type.ime());
                boolean typing = insets.isVisible(android.view.WindowInsets.Type.ime());
                header.setPadding(dp(16), dp(4) + bars.top, dp(16), dp(4));
                root.setPadding(bars.left, 0, bars.right, Math.max(bars.bottom, ime.bottom));

                if(typing&&dockPrompt.hasFocus())UiMotion.hide(quickOptions);
            } else {
                header.setPadding(dp(20), dp(12) + insets.getSystemWindowInsetTop(), dp(20), dp(12));
                root.setPadding(insets.getSystemWindowInsetLeft(),0,insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        root.requestApplyInsets();
        // Settings use three focused pages instead of nested cards and disclosure buttons.
        createPage.removeAllViews();
        LinearLayout settingsTabs = row(); settingsTabs.setPadding(dp(4), dp(4), dp(4), dp(4));settingsTabs.setBackground(FlowDesign.shape(this,FlowDesign.SURFACE,22,false));
        createPage.addView(settingsTabs);gap(createPage,16);
        LinearLayout generationSettings = column(), coverSettings = column(), serverSettings = column();
        LinearLayout[] settingPages = {generationSettings, coverSettings, serverSettings};
        String[] settingNames = {UiLanguage.t("Geração"), UiLanguage.t("App"), UiLanguage.t("Conexão")};
        int settingsIndex=getPreferences(0).getInt("settings_tab",0);
        Button[] settingButtons = new Button[3];
        for (int i = 0; i < 3; i++) {
            final int selected = i;
            Button tab = button(settingNames[i], i == settingsIndex ? FIELD : Color.TRANSPARENT, i == settingsIndex ? TEXT : MUTED);
            settingButtons[i] = tab;
            settingsTabs.addView(tab, new LinearLayout.LayoutParams(0, dp(48), 1));
            createPage.addView(settingPages[i]); settingPages[i].setVisibility(i == settingsIndex ? View.VISIBLE : View.GONE);
            tab.setOnClickListener(v -> {
                resetVersionTaps();
                boolean changed=settingPages[selected].getVisibility()!=View.VISIBLE;
                getPreferences(0).edit().putInt("settings_tab",selected).apply();
                for (int j = 0; j < 3; j++) {
                    settingPages[j].setVisibility(j == selected ? View.VISIBLE : View.GONE);
                    settingButtons[j].setBackground(FlowDesign.pill(this,j == selected ? FIELD : Color.TRANSPARENT,20,false));
                    settingButtons[j].setTextColor(j == selected ? TEXT : MUTED);
                }
                if(changed)UiMotion.enter(settingPages[selected]);
            });
        }
        workflowCard.removeView(workflowOptions); workflowCard.removeView(workflowDetails);
        workflowCard.setPadding(dp(16), dp(12), dp(16), dp(12));
        workflowNameView.setTextSize(16);
        memorySettings=column();generationSettings.addView(memorySettings);buildMemorySettings();gap(generationSettings,16);
        advancedSettings=column();generationSettings.addView(advancedSettings);
        loraButton = button(UiLanguage.t("LoRAs · selecionar e ajustar"), FIELD, TEXT);
        loraButton.setOnClickListener(v -> openLoras());
        try{helpCenter=new HelpCenter(this,()->{settingButtons[2].performClick();openSettings();});}catch(Exception error){android.util.Log.e("bwdacc","Bundled tutorials unavailable",error);}
        if(helpCenter!=null){
            LinearLayout learning=settingsSection(coverSettings,"book",helpCenter.title(),helpCenter.subtitle());
            Button tutorials=button(UiLanguage.t("Explorar tutoriais"),Color.TRANSPARENT,TEXT);FlowDesign.labelIcon(tutorials,"book");tutorials.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            learning.addView(tutorials,new LinearLayout.LayoutParams(-1,dp(48)));tutorials.setOnClickListener(v->{if(settingsDialog!=null)settingsDialog.dismiss();helpCenter.library();});
        }
        LinearLayout languageCard=settingsSection(coverSettings,"settings",UiLanguage.t("Interface"),"");
        Button language=button(UiLanguage.display(this),Color.TRANSPARENT,TEXT);language.setCompoundDrawables(null,null,FlowDesign.icon(this,"down",18),null);language.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        languageCard.addView(language,new LinearLayout.LayoutParams(-1,dp(48)));language.setOnClickListener(v->chooseLanguage());
        android.widget.Switch light=new android.widget.Switch(this);light.setText(UiLanguage.t("Luz ao inclinar"));light.setTextColor(TEXT);light.setTextSize(13);light.setTypeface(Branding.secondary(this,500));light.setChecked(getPreferences(0).getBoolean("motion_light",true));languageCard.addView(light,new LinearLayout.LayoutParams(-1,dp(48)));
        light.setOnCheckedChangeListener((v,on)->{getPreferences(0).edit().putBoolean("motion_light",on).apply();if(appResumed)motionLight.start(on);});
        LinearLayout playback=settingsSection(coverSettings,"play",UiLanguage.t("Reprodução"),UiLanguage.t("Vídeos visíveis começam no mudo."));
        android.widget.Switch autoplay=new android.widget.Switch(this);autoplay.setText(UiLanguage.t("Autoplay"));autoplay.setTextColor(TEXT);autoplay.setChecked(galleryAutoplay);playback.addView(autoplay,new LinearLayout.LayoutParams(-1,dp(48)));
        autoplay.setOnCheckedChangeListener((v,on)->{galleryAutoplay=on;getPreferences(0).edit().putBoolean("gallery_autoplay",on).apply();gallery.setPlaybackEnabled(on&&appResumed);syncFeedPlayback();});
        privacyOptions=column();LinearLayout privacy=privacyOptions;privacy.setPadding(dp(16),dp(8),dp(16),dp(8));
        privacy.addView(text(UiLanguage.t("Capas SFW protegem mídias privadas."),12,MUTED,false));
        privacySettings.removeView(coversSetting);coversSetting.setText(UiLanguage.t("Capas stealth"));privacy.addView(coversSetting,new LinearLayout.LayoutParams(-1,dp(48)));
        moveSettingsView(coverCount,privacy);coverCount.setTextSize(11);coverCount.setPadding(0,dp(2),0,dp(8));pickCovers.setText(UiLanguage.t("Escolher capas de reserva"));pickCovers.setBackgroundColor(Color.TRANSPARENT);FlowDesign.labelIcon(pickCovers,"image");pickCovers.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);moveSettingsView(pickCovers,privacy);
        Button concealOwl=button(UiLanguage.t("Ocultar coruja"),Color.TRANSPARENT,MUTED);FlowDesign.labelIcon(concealOwl,"owl-sleep");concealOwl.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        privacy.addView(concealOwl,new LinearLayout.LayoutParams(-1,dp(48)));concealOwl.setOnClickListener(v->{
            if(!hideAllNsfw)toggleNsfwVisibility();
            owlControlsUnlocked=false;getPreferences(0).edit().putBoolean("owl_controls_unlocked",false).putBoolean("hide_all_nsfw",true).apply();
            updatePrivacyButton();if(privacyOptionsDialog!=null)privacyOptionsDialog.dismiss();
        });
        LinearLayout storage=settingsSection(coverSettings,"folder",UiLanguage.t("Armazenamento"),UiLanguage.t("Cofre criptografado neste aparelho"));
        Button vaultInfo=button(UiLanguage.t("Sobre o cofre"),Color.TRANSPARENT,MUTED);FlowDesign.labelIcon(vaultInfo,"info");vaultInfo.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        storage.addView(vaultInfo,new LinearLayout.LayoutParams(-1,dp(44)));vaultInfo.setOnClickListener(v->new FlowDialogs.Builder(this)
            .setTitle(UiLanguage.t("Cofre deste aparelho"))
            .setMessage(UiLanguage.t("Imagens, vídeos, miniaturas, prompts e referências ficam criptografados no armazenamento do app.\n\nCrie um backup e guarde seu código separado antes de trocar de aparelho. Sem uma cópia válida e seu código, perder a chave pode impedir o acesso ao acervo.\n\nOs arquivos no computador ou servidor não são apagados por esse cofre."))
            .setPositiveButton(UiLanguage.t("Entendi"),null).show());
        Button backup=button(UiLanguage.t("Backup e recuperação"),Color.TRANSPARENT,TEXT);FlowDesign.labelIcon(backup,"folder");backup.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        storage.addView(backup,new LinearLayout.LayoutParams(-1,dp(48)));backup.setOnClickListener(v->{saveDraft();if(versionViewer!=null)versionViewer.saveEditDraft();startActivity(new Intent(this,VaultBackupActivity.class));});
        LinearLayout retention=row();retention.setGravity(Gravity.CENTER_VERTICAL);TextView retentionLabel=text(UiLanguage.t("Prompter sem like"),14,TEXT,false);retentionLabel.setGravity(Gravity.CENTER_VERTICAL);retention.addView(retentionLabel,new LinearLayout.LayoutParams(0,dp(44),1));retention.addView(text(UiLanguage.t("24 h"),14,MUTED,false));storage.addView(retention);
        storage.addView(text(UiLanguage.t("Itens salvos e arquivos do ComfyUI permanecem."),12,MUTED,false));gap(storage,8);
        Button sanitize=button(UiLanguage.t("Limpar gerações"),Color.TRANSPARENT,0xFFFFA9A9);FlowDesign.labelIcon(sanitize,"delete");sanitize.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        sanitize.setContentDescription(UiLanguage.t("Sanitize: apagar todas as gerações do app"));storage.addView(sanitize,new LinearLayout.LayoutParams(-1,dp(52)));sanitize.setOnClickListener(v->confirmSanitize());
        LinearLayout about=settingsSection(coverSettings,"info",UiLanguage.t("Sobre o app"),"");
        LinearLayout appVersion=row();appVersion.setGravity(Gravity.CENTER_VERTICAL);appVersion.setPadding(dp(2),dp(8),dp(2),dp(8));
        TextView versionLabel=text(UiLanguage.t("Versão do app"),14,TEXT,false);appVersion.addView(versionLabel,new LinearLayout.LayoutParams(0,-2,1));
        TextView versionValue=text(installedAppVersion(),12,MUTED,false);versionValue.setGravity(Gravity.END);appVersion.addView(versionValue,new LinearLayout.LayoutParams(-2,-2));
        appVersion.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x20FFFFFF),null,round(Color.WHITE,12,0)));
        appVersion.setFocusable(true);appVersion.setContentDescription(UiLanguage.t("Versão do app")+" "+installedAppVersion());appVersion.setOnClickListener(v->tapAppVersion());
        about.addView(appVersion,new LinearLayout.LayoutParams(-1,dp(52)));
        serverSettings.addView(text(UiLanguage.t("Servidor"), 17, TEXT, true)); gap(serverSettings, 8);
        moveSettingsView(connection, serverSettings); gap(serverSettings, 12);
        moveSettingsView(serverToggle, serverSettings);
        serverToggle.setText(UiLanguage.t("Pareamento"));
        moveSettingsView(connectionPanel, serverSettings); gap(serverSettings, 12);
        moveSettingsView(queue, serverSettings);
        gap(serverSettings,16);Button monitor=button(UiLanguage.t("Fila"),FIELD,TEXT);FlowDesign.labelIcon(monitor,"queue");serverSettings.addView(monitor,new LinearLayout.LayoutParams(-1,dp(56)));
        monitor.setOnClickListener(v->{try{String address=checkedUrl();if(serverMonitor!=null)serverMonitor.close();serverMonitor=new ServerMonitor(this,address,url->requestJson("GET",url,null),id->{Generation g=findGeneration(id);return g==null?null:g.workflowName;});}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}});
        restoreWorkflow();
        if (workflow == null) showFields();
        dockPrompt.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (bindingFields) return;
                handler.removeCallbacks(persistDraft);
                handler.postDelayed(persistDraft, 250);
            }
            public void afterTextChanged(android.text.Editable text) { }
        });
        renderFeed();
        showTab(true);
        handler.post(expireTrials);
        if (getPreferences(0).contains("server")) connect();
        else { workflowDetails.setVisibility(View.VISIBLE); connectionPanel.setVisibility(View.VISIBLE); }
        if(state!=null){activeProjectId=state.getString("activeProjectId","");fragmentReturnTab=Math.max(0,Math.min(3,state.getInt("fragmentReturnTab",0)));selectTab(state.getInt("selectedTab",0),false);}
        boolean reopenSettings=UiLanguage.reopenSettings(this);
        if(reopenSettings)handler.post(this::openSettings);
        Appearance.refresh(root);
        if(helpCenter!=null)handler.post(()->{if(!isFinishing()&&!isDestroyed())helpCenter.start(state,reopenSettings);});
    }

    private String sectionName(int tab){return new String[]{UiLanguage.t("Feed"),UiLanguage.t("Galeria"),UiLanguage.t("Prompter"),UiLanguage.t("Projetos"),UiLanguage.t("Fragmentos")}[Math.max(0,Math.min(4,tab))];}
    private void showNavigation(){
        hideKeyboard(dockPrompt);LinearLayout menu=column();menu.setPadding(dp(8),dp(8),dp(8),dp(8));
        android.widget.PopupWindow popup=new android.widget.PopupWindow(menu,dp(224),-2,true);popup.setBackgroundDrawable(round(FlowDesign.SURFACE,22,BORDER));popup.setElevation(dp(12));popup.setOutsideTouchable(true);
        String[] icons={"feed","grid","flask","folder","layers"};
        for(int i=0;i<5;i++){final int tab=i;Button choice=button(sectionName(i),i==selectedTab?FIELD:Color.TRANSPARENT,TEXT);choice.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);choice.setCompoundDrawables(FlowDesign.icon(this,icons[i],18),null,i==selectedTab?FlowDesign.icon(this,"check",16):null,null);choice.setCompoundDrawablePadding(dp(12));choice.setSelected(i==selectedTab);menu.addView(choice,new LinearLayout.LayoutParams(-1,dp(48)));
            choice.setOnClickListener(v->{popup.dismiss();if(tab==3){saveDraft();activeProjectId="";selectTab(3,false);if(mainFold!=null)mainFold.setCollapsed(true);}else selectTab(tab);});}
        popup.setAnimationStyle(0);popup.setEnterTransition(UiMotion.menuTransition(sectionButton,dp(208)));popup.setExitTransition(UiMotion.menuTransition(sectionButton,dp(208)));
        popup.showAsDropDown(sectionButton,sectionButton.getWidth()-dp(224),dp(4));
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(mainFold!=null&&versionViewer==null){
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){browseStartX=event.getRawX();browseStartY=event.getRawY();android.graphics.Rect area=new android.graphics.Rect();View content=galleryPage!=null&&galleryPage.isShown()?gallery:mainScroll;browsingContent=content!=null&&content.getGlobalVisibleRect(area)&&area.contains((int)browseStartX,(int)browseStartY);}
            else if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&browsingContent&&Math.abs(event.getRawY()-browseStartY)>dp(24)&&Math.abs(event.getRawY()-browseStartY)>Math.abs(event.getRawX()-browseStartX)){
                browsingContent=false;UiMotion.hide(quickOptions);mainFold.setCollapsed(true);
            }else if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)browsingContent=false;
        }
        return super.dispatchTouchEvent(event);
    }
    private void showTab(boolean feed) {
        if (feed) {
            if (settingsDialog != null) settingsDialog.dismiss();
            selectTab(0);
        } else openSettings();
    }

    private String mainDraftKey(){return (selectedTab==2?"prompter_draft_":selectedTab==3&&!activeProjectId.isEmpty()?"project_draft_"+activeProjectId+"_":"draft_")+workflowName;}
    private void selectTab(int selected) {
        selectTab(selected,true);
    }
    private void selectTab(int selected,boolean save) {
        boolean changed=selectedTab!=selected;
        if(save&&selectedTab!=4)saveDraft();if(selected==4&&selectedTab!=4&&save)fragmentReturnTab=selectedTab;handler.removeCallbacks(persistDraft);selectedTab=selected;
        selecting=false;selectedGenerations.clear();
        for(Runnable hide:hideReveals)hide.run();hideGalleryReveal();hideKeyboard(dockPrompt);
        headerTitle.setText(UiLanguage.t("bwdacc"));boolean grid=selected==1||selected==3&&!activeProjectId.isEmpty();
        mainScroll.setVisibility(grid||selected==4?View.GONE:View.VISIBLE);galleryPage.setVisibility(grid?View.VISIBLE:View.GONE);
        fragmentsPage.setVisibility(selected==4?View.VISIBLE:View.GONE);if(selected==4){fragmentsPage.removeAllViews();fragmentsPage.addView(referenceStudio.library(0),new android.widget.FrameLayout.LayoutParams(-1,-1));}
        feedPage.setVisibility(selected==0?View.VISIBLE:View.GONE);prompterPage.setVisibility(selected==2?View.VISIBLE:View.GONE);projectsPage.setVisibility(selected==3&&!grid?View.VISIBLE:View.GONE);
        sectionButton.setText(sectionName(selected));sectionButton.setContentDescription(UiLanguage.t("Navegar · ")+sectionName(selected));if(save&&mainFold!=null)mainFold.setCollapsed(true);
        if(selected!=4){bindingFields=true;dockPrompt.setText(getPreferences(0).getString(mainDraftKey(),""));bindingFields=false;mainReferences.bind();}
        UiMotion.hide(quickOptions);generationDock.setVisibility(selected==4||selected==3&&!grid?View.GONE:View.VISIBLE);
        renderFeed();gallery.setPlaybackEnabled(galleryAutoplay&&appResumed&&grid);mainScroll.post(()->mainScroll.scrollTo(0,0));
        if(changed)UiMotion.enter(selected==4?fragmentsPage:grid?galleryPage:mainScroll);
    }
    private boolean published(Generation item){return !item.temporary&&visibleGeneration(item);}
    private List<Generation> trialItems(){List<Generation> result=new ArrayList<>();for(Generation item:generations)if(prompterSession.equals(item.trialSession)&&visibleGeneration(item))result.add(item);return result;}
    private final class Destination {
        final String session,project;
        Destination(VersionViewer viewer){session=(viewer==null?selectedTab==2:viewer.prompterContext)?prompterSession:"";project=selectedTab==3?activeProjectId:"";}
        void apply(Generation item,Generation source){
            item.trialSession=session;item.temporary=!session.isEmpty();
            if(source!=null)item.projectIds.addAll(source.projectIds);
            if(!project.isEmpty()&&project(project)!=null)item.projectIds.add(project);
        }
    }
    private void saveTrial(Generation item){
        if(!item.temporary||!"done".equals(item.state)||!thumbFile(item.id).isFile())return;
        long expiry=item.expiresAt;item.temporary=false;item.expiresAt=0;
        if(!saveFeed()){item.temporary=true;item.expiresAt=expiry;Toast.makeText(this,UiLanguage.t("Falha ao salvar. Tente novamente."),Toast.LENGTH_LONG).show();return;}
        Toast.makeText(this,UiLanguage.t("Salva no Feed e na Galeria"),Toast.LENGTH_SHORT).show();renderFeed();if(versionViewer!=null)versionViewer.refresh();
    }
    void cleanupExpiredTrials(){
        if(runButton==null||!runButton.isEnabled()||videoSubmitting||!editUploads.isEmpty())return;
        try{JSONArray feed=new JSONArray();for(Generation item:generations)feed.put(item.toJson());
            java.util.Set<String> pinned=new java.util.HashSet<>();if(mainReferences!=null)pinned.addAll(mainReferences.ids);if(versionViewer!=null){pinned.add(versionViewer.currentId);pinned.addAll(versionViewer.references.ids);}
            java.util.Set<String> expired=TrialPolicy.expiredIds(feed,System.currentTimeMillis(),pinned);if(expired.isEmpty())return;
            List<Generation> previous=new ArrayList<>(generations);generations.removeIf(item->expired.contains(item.id));
            if(!saveFeed()){generations.clear();generations.addAll(previous);return;}
            for(String id:expired){GenerationRecipe.delete(getFilesDir(),id);recipeCache.remove(id);VaultFiles.delete(imageFile(id));VaultFiles.delete(thumbFile(id));VaultFiles.delete(videoFile(id));VaultFiles.delete(new File(videoFile(id).getPath()+".part"));}
            renderFeed();
        }catch(Exception ignored){}
    }
    private void renderPrompter(){
        prompterPage.removeAllViews();LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);
        List<Generation> items=trialItems();header.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        Button clear=button("",Color.TRANSPARENT,TEXT);FlowDesign.symbol(clear,"delete",UiLanguage.t("Limpar feed do Prompter"));header.addView(clear,new LinearLayout.LayoutParams(dp(48),dp(48)));
        clear.setOnClickListener(v->confirmClearPrompter());
        prompterPage.addView(header);prompterList.removeAllViews();prompterPage.addView(prompterList);
        if(items.isEmpty()){TextView empty=text(UiLanguage.t("Experimente seu prompt\nEle permanece no compositor após gerar. Testes sem like ficam aqui por 24 horas."),15,MUTED,false);empty.setPadding(dp(16),dp(36),dp(16),dp(36));prompterList.addView(empty);}
        for(int i=0;i<Math.min(40,items.size());i++)addGenerationCard(items.get(i),prompterList,true);
        if(items.size()>40){Button all=button(UiLanguage.t("Abrir sequência completa"),FIELD,TEXT);prompterList.addView(all);all.setOnClickListener(v->openViewer(items.get(0)));}
    }

    private boolean visibleGeneration(Generation item) { return !hideAllNsfw || !item.nsfw; }
    private boolean covered(Generation item) { return item.nsfw && privateCoversEnabled; }

    private void toggleNsfwVisibility() {
        if(!owlControlsUnlocked)return;
        if(versionViewer!=null){versionViewer.cancelMotion();versionViewer.conceal();versionViewer.stopVideo();}
        hideAllNsfw = !hideAllNsfw;
        Appearance.mode(this,!hideAllNsfw);Appearance.refresh(getWindow().getDecorView());
        if(settingsDialog!=null)Appearance.refresh(settingsDialog.getWindow().getDecorView());
        getPreferences(0).edit().putBoolean("hide_all_nsfw", hideAllNsfw).apply();
        if(hideAllNsfw)disablePrivateLoras();
        updateLoraButton();
        selectedGenerations.clear();
        hideGalleryReveal(); renderFeed(); updatePrivacyButton();
        if(mainReferences!=null)mainReferences.refresh();if(versionViewer!=null)versionViewer.references.refresh();
    }

    private void disablePrivateLoras(){
        android.content.SharedPreferences.Editor changes=getPreferences(0).edit();
        for(Map.Entry<String,?> value:getPreferences(0).getAll().entrySet())if(value.getKey().startsWith("loras_")&&value.getValue() instanceof String)try{
            JSONObject choices=new JSONObject((String)value.getValue());if(LoraPrivacy.disableChoices(choices)>0)changes.putString(value.getKey(),choices.toString());
        }catch(Exception ignored){}
        changes.apply();
        if(workflow!=null)try{if(LoraPrivacy.enforce(workflow)>0)writeWorkflow(new File(getFilesDir(),"workflow.json"),workflow);}catch(Exception ignored){}
    }
    private void updateMainPrivacy() {
        boolean secure = !hideAllNsfw && !privateCoversEnabled;
        if (secure) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    private void privacyIcon(Button button) {
        button.setText(""); button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackgroundColor(Color.TRANSPARENT);
        android.graphics.drawable.Drawable eye = new android.graphics.drawable.Drawable() {
            final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(android.graphics.Canvas canvas) {
                android.graphics.Rect b = getBounds();
                canvas.save(); canvas.translate(b.left, b.top); canvas.scale(b.width() / 24f, b.height() / 24f);
                FlowDesign.drawIcon(canvas,paint,hideAllNsfw ? "owl-sleep" : "owl",hideAllNsfw ? MUTED : 0xFFD2C6FF);
                canvas.restore();
            }
            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
        eye.setBounds(0, 0, dp(24), dp(24)); button.setCompoundDrawables(eye, null, null, null);
        button.setContentDescription(hideAllNsfw ? UiLanguage.t("NSFW desativado. Ativar modo privado") : UiLanguage.t("NSFW ativado. Desativar modo privado"));
    }

    private void actionIcon(Button button, boolean animation) {
        button.setText(""); button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setContentDescription(animation ? UiLanguage.t("Animar imagem") : UiLanguage.t("Editar imagem"));
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x30FFFFFF), round(FIELD, 24, 0), null));
        android.graphics.drawable.Drawable icon = new android.graphics.drawable.Drawable() {
            final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(android.graphics.Canvas canvas) {
                android.graphics.Rect b = getBounds(); canvas.save(); canvas.translate(b.left, b.top);
                canvas.scale(b.width() / 24f, b.height() / 24f);
                paint.setColor(TEXT); paint.setStrokeWidth(1.8f); paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                android.graphics.Path path = new android.graphics.Path();
                if (animation) {
                    canvas.drawRoundRect(2, 5, 16, 19, 3, 3, paint);
                    path.moveTo(16, 9); path.lineTo(22, 6); path.lineTo(22, 18); path.lineTo(16, 15);
                } else {
                    path.moveTo(4, 16); path.lineTo(15, 5); path.lineTo(19, 9); path.lineTo(8, 20);
                    path.lineTo(3, 21); path.close();
                    path.moveTo(12, 8); path.lineTo(16, 12);
                }
                canvas.drawPath(path, paint); canvas.restore();
            }
            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
            @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        };
        icon.setBounds(0, 0, dp(24), dp(24)); button.setCompoundDrawables(icon, null, null, null);
    }

    private void updatePrivacyButton() { privacyIcon(privacyButton);privacyButton.setVisibility(owlControlsUnlocked?View.VISIBLE:View.GONE); }

    private String installedAppVersion(){
        try{android.content.pm.PackageInfo info=getPackageManager().getPackageInfo(getPackageName(),0);long code=android.os.Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;return info.versionName+" ("+code+")";}
        catch(android.content.pm.PackageManager.NameNotFoundException error){return "—";}
    }
    private void resetVersionTaps(){versionTapCount=0;lastVersionTap=0;}
    private void tapAppVersion(){
        long now=android.os.SystemClock.uptimeMillis();if(now-lastVersionTap>1800)versionTapCount=0;lastVersionTap=now;
        if(++versionTapCount<6)return;resetVersionTaps();
        boolean newlyVisible=!owlControlsUnlocked;owlControlsUnlocked=true;getPreferences(0).edit().putBoolean("owl_controls_unlocked",true).apply();
        updatePrivacyButton();if(newlyVisible)UiMotion.enter(privacyButton);
        if(privacyOptionsDialog==null){
            privacyOptionsDialog=new Dialog(this);LinearLayout sheet=column();sheet.setPadding(dp(12),dp(12),dp(12),dp(20));
            LinearLayout heading=row();heading.setGravity(Gravity.CENTER_VERTICAL);heading.setPadding(dp(12),0,dp(4),dp(8));
            heading.addView(text(UiLanguage.t("Privacidade"),20,TEXT,true),new LinearLayout.LayoutParams(0,-2,1));
            Button close=button("",Color.TRANSPARENT,ACCENT);FlowDesign.symbol(close,"check",UiLanguage.t("Concluir ajustes"));close.setOnClickListener(v->privacyOptionsDialog.dismiss());heading.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));sheet.addView(heading);
            ScrollView scroll=new ScrollView(this);scroll.addView(privacyOptions);sheet.addView(scroll,new LinearLayout.LayoutParams(-1,-2));privacyOptionsDialog.setContentView(sheet);
        }
        UiMotion.dialog(privacyOptionsDialog);privacyOptionsDialog.show();Appearance.refresh(privacyOptionsDialog.getWindow().getDecorView());
        privacyOptionsDialog.getWindow().setBackgroundDrawable(round(BG,32,BORDER));privacyOptionsDialog.getWindow().setGravity(Gravity.BOTTOM);privacyOptionsDialog.getWindow().setLayout(-1,-2);
    }

    private void moveSettingsView(View view, LinearLayout destination) {
        if (view.getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup)view.getParent()).removeView(view);
        destination.addView(view);
    }

    private LinearLayout settingsSection(LinearLayout parent,String icon,String title,String detail){
        if(parent.getChildCount()>0)gap(parent,12);LinearLayout group=column();group.setPadding(dp(16),dp(14),dp(16),dp(14));group.setBackground(round(FlowDesign.SURFACE,24,0));
        LinearLayout heading=row();heading.setGravity(Gravity.CENTER_VERTICAL);TextView name=text(title,15,TEXT,true);name.setCompoundDrawables(FlowDesign.icon(this,icon,19),null,null,null);name.setCompoundDrawablePadding(dp(10));heading.addView(name,new LinearLayout.LayoutParams(-1,dp(32)));group.addView(heading);
        if(!detail.isEmpty()){TextView hint=text(detail,12,MUTED,false);hint.setPadding(0,dp(2),0,dp(10));group.addView(hint);}parent.addView(group,new LinearLayout.LayoutParams(-1,-2));return group;
    }
    private void chooseLanguage(){
        new FlowDialogs.Builder(this).setTitle(UiLanguage.t("Idioma do app")).setSingleChoiceItems(UiLanguage.NAMES,UiLanguage.index(this),(dialog,index)->{
            if(!editUploads.isEmpty()||!runButton.isEnabled()){Toast.makeText(this,UiLanguage.t("Aguarde o envio terminar para mudar o idioma."),Toast.LENGTH_SHORT).show();return;}
            saveDraft();if(versionViewer!=null)versionViewer.saveEditDraft();dialog.dismiss();UiLanguage.select(this,index);
        }).setNegativeButton(UiLanguage.t("Cancelar"),null).show();
    }

    private void openSettings() {
        resetVersionTaps();
        for (Runnable hide : hideReveals) hide.run();
        hideGalleryReveal();
        dockPrompt.clearFocus();
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(dockPrompt.getWindowToken(), 0);
        if (settingsDialog == null) {
            settingsDialog = new Dialog(this);
            settingsDialog.setOnDismissListener(dialog->resetVersionTaps());
            LinearLayout sheet = column();
            sheet.setPadding(dp(12), dp(12), dp(12), dp(12));
            LinearLayout title = row();
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setPadding(dp(12), 0, dp(4), dp(8));
            title.addView(text(UiLanguage.t("Ajustes"), 20, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
            Button close = button("", Color.TRANSPARENT, ACCENT);FlowDesign.symbol(close,"check",UiLanguage.t("Concluir ajustes"));
            close.setOnClickListener(v -> settingsDialog.dismiss());
            title.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));
            sheet.addView(title);
            ScrollView scroll = new ScrollView(this);
            scroll.addView(createPage);
            sheet.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            settingsDialog.setContentView(sheet);
        }
        UiMotion.dialog(settingsDialog);settingsDialog.show();Appearance.refresh(settingsDialog.getWindow().getDecorView());
        settingsDialog.getWindow().setBackgroundDrawable(round(BG, 32, BORDER));
        settingsDialog.getWindow().setGravity(Gravity.BOTTOM);
        settingsDialog.getWindow().setLayout(-1, (int)(getResources().getDisplayMetrics().heightPixels * .86f));
        settingsDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    private void initializeReferences(){
        referenceStore=new ReferencePreset.Store(this,getPreferences(0));
        for(Generation item:generations)if(item.nsfw)referenceStore.protect(item.id);referenceStore.cleanup();
        referenceJobs=new ReferenceJobs(referenceStore,new ReferenceJobs.Server(){
            public JSONObject json(String method,String url,JSONObject body)throws Exception{return requestJson(method,url,body);}
            public String upload(String address,File file)throws Exception{return uploadEditInput(address,file);}
            public byte[] download(String address,JSONObject image)throws Exception{return requestBytes("GET",address+imagePath(image),null,30_000_000);}
        });
        referenceStudio=new ReferenceStudio(this,referenceStore,referenceJobs,new ReferenceStudio.Host(){
            public void audio(ReferencePreset p){chooseLipsyncMedia(p);}
            public String tag(ReferencePreset p){return referenceTag(p);}
            public void insert(String tag){if(selectedTab==4)selectTab(fragmentReturnTab,false);if(mainFold!=null)mainFold.setCollapsed(false);insertTag(tag);}
            public String address(){return connected?baseUrl:null;}
            public boolean nsfw(){return !hideAllNsfw;}
            public void composition(ReferencePreset p){
                try{
                    if(selectedTab==4)selectTab(fragmentReturnTab,false);
                    if(!BoxLayout.supported(workflow)){Toast.makeText(MainActivity.this,UiLanguage.t("Composição salva. Escolha um workflow com boxes para usá-la."),Toast.LENGTH_LONG).show();return;}
                    JSONObject layout=GenerationRecipe.copy(p.layout);layout.put("private",p.hidden());BoxLayout.regions(layout);
                    if(versionViewer!=null)versionViewer.dialog.dismiss();
                    getPreferences(0).edit().putString("layout_"+mainDraftKey(),layout.toString()).apply();rebuildQuickOptions();
                    if(mainFold!=null)mainFold.setCollapsed(false);Toast.makeText(MainActivity.this,UiLanguage.t("Composição aplicada."),Toast.LENGTH_SHORT).show();
                }catch(Exception error){Toast.makeText(MainActivity.this,error.getMessage(),Toast.LENGTH_LONG).show();}
            }
            public Bitmap preview(ReferencePreset p,ReferencePreset.Picture image,boolean reveal){
                if(!reveal&&(p.privateCover||image!=null&&image.hidden)){Generation safe=new Generation();safe.id=p.id;safe.workflowName="Referências";return coverBitmap(safe);}
                if(image!=null)return galleryThumbnail(referenceStore.file(image.file));
                return p.legacyCover.isEmpty()?null:galleryThumbnail(characterCover(p.legacyCover));
            }
            public void choose(boolean library,int remaining,ReferenceStudio.Pick callback){
                if(library){pickPresetLibrary(remaining,callback);return;}
                pendingPresetPick=callback;pendingPresetLimit=remaining;
                if(!PhotoPicker.launch(MainActivity.this,remaining,PICK_PRESET_IMAGES)){pendingPresetPick=null;Toast.makeText(MainActivity.this,UiLanguage.t("Nenhuma galeria de fotos disponível neste aparelho."),Toast.LENGTH_LONG).show();}
            }
        });
    }

    private ReferencePreset.Picture saveReferenceImage(InputStream input,String source,boolean hidden)throws Exception{
        byte[] bytes=read(input,30_000_000);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IllegalArgumentException(UiLanguage.t("Imagem inválida."));
        BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>2048)options.inSampleSize*=2;
        Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap==null)throw new IllegalArgumentException(UiLanguage.t("Não foi possível abrir a imagem."));
        if(android.os.Build.VERSION.SDK_INT>=24)try{
            android.media.ExifInterface exif=new android.media.ExifInterface(new java.io.ByteArrayInputStream(bytes));int orientation=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);android.graphics.Matrix matrix=new android.graphics.Matrix();
            switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(270);matrix.postScale(-1,1);break;case 8:matrix.setRotate(270);break;}
            if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;}
        }catch(java.io.IOException ignored){}
        String name=UUID.randomUUID()+".png";File target=referenceStore.file(name);
        try(OutputStream out=VaultFiles.output(target)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new java.io.IOException("Falha ao salvar imagem.");}
        finally{bitmap.recycle();}
        return new ReferencePreset.Picture(name,source,hidden);
    }
    private void importPresetImages(Intent data){
        final ReferenceStudio.Pick callback=pendingPresetPick;pendingPresetPick=null;if(callback==null)return;
        final List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount()&&uris.size()<pendingPresetLimit;i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());
        final boolean hidden=!hideAllNsfw;worker.execute(()->{List<ReferencePreset.Picture> images=new ArrayList<>();int failed=0;
            for(Uri uri:uris)try(InputStream in=getContentResolver().openInputStream(uri)){images.add(saveReferenceImage(in,"",hidden));}catch(Exception e){failed++;}
            final int failures=failed;runOnUiThread(()->{callback.accept(images);if(failures>0)Toast.makeText(this,failures+UiLanguage.t(" imagens não puderam ser importadas."),Toast.LENGTH_LONG).show();});
        });
    }
    private void pickPresetLibrary(int limit,ReferenceStudio.Pick callback){
        List<Generation> images=new ArrayList<>();for(Generation item:generations)if("image".equals(item.mediaKind)&&"done".equals(item.state)&&imageFile(item.id).exists()&&(!item.nsfw||!hideAllNsfw))images.add(item);
        java.util.Set<String> selected=new java.util.LinkedHashSet<>();LinearLayout sheet=column();sheet.setPadding(dp(16),dp(16),dp(16),dp(16));sheet.addView(text(UiLanguage.t("Biblioteca"),22,TEXT,true));gap(sheet,12);
        android.widget.GridView grid=new android.widget.GridView(this);grid.setNumColumns(3);grid.setHorizontalSpacing(dp(8));grid.setVerticalSpacing(dp(8));sheet.addView(grid,new LinearLayout.LayoutParams(-1,0,1));Button done=button(UiLanguage.t("Adicionar imagens"),ACCENT,BG);done.setEnabled(false);gap(sheet,12);sheet.addView(done,new LinearLayout.LayoutParams(-1,dp(48)));Dialog picker=styleSheet(sheet);picker.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        grid.setAdapter(new android.widget.BaseAdapter(){public int getCount(){return images.size();}public Object getItem(int p){return images.get(p);}public long getItemId(int p){return p;}
            public View getView(int position,View recycled,android.view.ViewGroup parent){Generation item=images.get(position);android.widget.FrameLayout tile=new android.widget.FrameLayout(MainActivity.this);tile.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,dp(110)));tile.setBackground(round(FIELD,16,0));tile.setClipToOutline(true);ImageView image=new ImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(item.nsfw?coverBitmap(item):galleryThumbnail(thumbFile(item.id)));tile.addView(image,new android.widget.FrameLayout.LayoutParams(-1,-1));tile.setContentDescription(UiLanguage.t("Selecionar referência ")+(position+1));
                if(selected.contains(item.id)){TextView badge=text("✓",20,TEXT,true);badge.setGravity(Gravity.CENTER);badge.setBackground(round(0xCC202123,14,0));android.widget.FrameLayout.LayoutParams lp=new android.widget.FrameLayout.LayoutParams(dp(30),dp(30),Gravity.TOP|Gravity.RIGHT);lp.setMargins(0,dp(5),dp(5),0);tile.addView(badge,lp);}
                tile.setOnClickListener(v->{if(selected.contains(item.id))selected.remove(item.id);else if(selected.size()<limit)selected.add(item.id);else{Toast.makeText(MainActivity.this,UiLanguage.t("Até ")+limit+UiLanguage.t(" imagens."),Toast.LENGTH_SHORT).show();return;}done.setEnabled(!selected.isEmpty());done.setText(UiLanguage.t("Adicionar ")+selected.size());notifyDataSetChanged();});return tile;}
        });
        if(images.isEmpty())sheet.addView(text(UiLanguage.t("Nenhuma imagem disponível. Use Imagens para importar."),14,MUTED,false));
        done.setOnClickListener(v->{List<Generation> chosen=new ArrayList<>();for(String id:selected){Generation item=findGeneration(id);if(item!=null)chosen.add(item);}picker.dismiss();worker.execute(()->{List<ReferencePreset.Picture> copied=new ArrayList<>();for(Generation item:chosen)try(InputStream input=VaultFiles.open(imageFile(item.id))){copied.add(saveReferenceImage(input,item.id,item.nsfw));}catch(Exception ignored){}runOnUiThread(()->callback.accept(copied));});});
    }

    private String stableHandle(String kind, String id, String name) {
        String key=kind+"_handles";
        JSONObject saved;try{saved=new JSONObject(getPreferences(0).getString(key,"{}"));}catch(Exception e){saved=new JSONObject();}
        String handle=saved.optString(id,"");if(!handle.isEmpty())return handle;
        java.util.Set<String> reserved=new java.util.HashSet<>();Iterator<String> keys=saved.keys();while(keys.hasNext())reserved.add(saved.optString(keys.next()));
        String base=CharacterMentions.handle(name);handle=base;int suffix=2;while(reserved.contains(handle))handle=base+"_"+(suffix++);
        try{saved.put(id,handle);}catch(Exception ignored){}getPreferences(0).edit().putString(key,saved.toString()).apply();return handle;
    }

    private java.util.Map<String,String> tagNames(char kind) {
        java.util.Map<String,String> tags=new java.util.LinkedHashMap<>();
        for(ReferencePreset p:referenceStore.all())if(!p.audio()&&!p.control()&&p.prefix()==kind)tags.put(referenceTag(p),p.name);
        return tags;
    }

    private String referenceTag(ReferencePreset p){return p.prefix()+stableHandle(p.kind,p.id,p.name);}
    private java.util.Map<String,String> tagDescriptions() {
        return tagDescriptions(versionViewer!=null&&versionViewer.dialog.isShowing()?versionViewer:null);
    }
    private java.util.Map<String,String> tagDescriptions(VersionViewer owner) {
        java.util.Map<String,String> tags=new java.util.LinkedHashMap<>();
        for(ReferencePreset p:referenceStore.all())if(!p.audio()&&!p.control())tags.put(referenceTag(p),p.promptText());
        for(Map.Entry<String,ReferencePreset> entry:frozenPresets(owner).entrySet()){ReferencePreset p=entry.getValue();tags.put(entry.getKey(),p.promptText());}return tags;
    }
    private List<ReferencePreset> taggedReferences(String prompt){
        return taggedReferences(prompt,versionViewer!=null&&versionViewer.dialog.isShowing()?versionViewer:null);
    }
    private List<ReferencePreset> taggedReferences(String prompt,VersionViewer owner){
        java.util.Set<String> tokens=PromptTags.tokens(prompt);List<ReferencePreset> selected=new ArrayList<>();
        Map<String,ReferencePreset> catalog=new HashMap<>();for(ReferencePreset p:referenceStore.all())if(!p.audio()&&!p.control())catalog.put(referenceTag(p).toLowerCase(Locale.ROOT),p);
        catalog.putAll(frozenPresets(owner));
        for(String token:tokens){ReferencePreset p=catalog.get(token);if(p!=null)selected.add(p.copy());}return selected;
    }

    private final Map<String,JSONObject> recipeCache=new HashMap<>();
    private final Map<String,Generation> restoredInputs=new HashMap<>();
    private GenerationStorage generationStorage;
    private boolean storageBusy;
    private String reuseKey(VersionViewer owner){return "reuse_"+(owner==null?mainDraftKey():owner.draftKey());}
    private JSONObject activeRecipe(VersionViewer owner){
        String id=getPreferences(0).getString(reuseKey(owner),"");if(id.isEmpty())return null;
        try{JSONObject recipe=recipeCache.get(id);if(recipe==null){recipe=GenerationRecipe.load(getFilesDir(),id);recipeCache.put(id,recipe);}return recipe;}catch(Exception e){return null;}
    }
    private Map<String,ReferencePreset> frozenPresets(VersionViewer owner){
        Map<String,ReferencePreset> out=new java.util.LinkedHashMap<>();JSONObject recipe=activeRecipe(owner);if(recipe==null)return out;
        JSONArray presets=recipe.optJSONArray("presets");if(presets!=null)for(int i=0;i<presets.length();i++)try{JSONObject entry=presets.getJSONObject(i);ReferencePreset p=ReferencePreset.parse(entry.getJSONObject("preset"));
            ReferencePreset current=referenceStore.get(p.id);if(current!=null){if(current.hidden())p.privateCover=true;p.usePermissions(current);}
            for(ReferencePreset.Picture picture:p.images){Generation source=findGeneration(picture.source);if(source!=null&&source.nsfw){picture.hidden=true;p.privateCover=true;}}
            out.put(entry.getString("tag").toLowerCase(Locale.ROOT),p);
        }catch(Exception ignored){}return out;
    }
    private JSONObject captureRecipe(VersionViewer owner,JSONObject template,String operation,List<Generation> refs,List<ReferencePreset> presets,String raw,String expanded,boolean hidden)throws Exception{
        String unavailable=operation.equals("video_extend")?MemoryProfiles.extensionUnavailable(memoryProfile()):MemoryProfiles.unavailable(template,memoryProfile());if(!unavailable.isEmpty())throw new IllegalArgumentException(unavailable);
        JSONObject options=recipeOptions(owner,template);
        JSONArray media=new JSONArray();for(Generation ref:refs)media.put(ref.toJson());JSONArray saved=new JSONArray();Map<String,ReferencePreset> frozen=frozenPresets(owner);
        for(ReferencePreset preset:presets){String tag=referenceTag(preset);for(Map.Entry<String,ReferencePreset> old:frozen.entrySet())if(old.getValue().id.equals(preset.id))tag=old.getKey();saved.put(new JSONObject().put("tag",tag).put("preset",preset.json()));}
        String source=operation.equals("video")?(refs.isEmpty()?"":refs.get(0).id):owner==null?"":owner.currentId;
        JSONObject snapshot=new JSONObject().put("version",1).put("operation",operation).put("sourceId",source)
            .put("workflowName",owner==null?workflowName:viewerAdvancedName(owner)).put("server",baseUrl).put("template",GenerationRecipe.copy(template))
            .put("promptTemplate",raw).put("prompt",expanded).put("nsfw",hidden).put("options",options).put("refs",media).put("presets",saved);
        JSONObject previous=activeRecipe(owner);if(previous!=null&&operation.equals(previous.optString("operation")))snapshot.put("inherited",previous);
        if(owner==null){for(Field field:fields)if("Ideia da imagem".equals(field.primaryLabel)){snapshot.put("promptNode",field.nodeId).put("promptKey",field.key);break;}}
        else snapshot.put("promptNode",owner.animationEditor?"8":"6").put("promptKey",owner.animationEditor?"prompt":"text");
        return snapshot;
    }
    private JSONObject recipeOptions(VersionViewer owner,JSONObject template)throws Exception{
        return new JSONObject().put("ratio",owner==null?currentRatio():owner.outputRatio()).put("imageMp",owner==null?currentImageMegapixels():owner.imageMegapixels)
            .put("quality",owner==null?videoQuality:owner.videoQuality).put("duration",owner==null?videoDuration:owner.editingVideo()?owner.editDurationIndex:owner.durationIndex)
            .put("batch",owner==null?batchCount:owner.batch).put("agent",agentEnabled(owner)).put("agentNsfw",!hideAllNsfw).put("firstLast",owner==null?videoFirstLast:owner.firstLastFrame)
            .put("turbo",owner==null?VideoWorkflow.turbo(template):owner.editingVideo()?owner.editTurbo:owner.turbo)
            .put("memoryProfile",memoryProfile()).put("layout",owner==null&&BoxLayout.supported(workflow)?currentBoxLayout():new JSONObject())
            .put("videoRange",owner!=null&&owner.extending?owner.extensionRange:new JSONObject());
    }
    private String agentKey(VersionViewer owner){return "agent_mode_"+(owner==null?workflowName:viewerAdvancedName(owner));}
    private JSONObject agentTemplate(VersionViewer owner){try{return owner==null?workflow:editTemplate(owner);}catch(Exception e){return null;}}
    private boolean agentEnabled(VersionViewer owner){return getPreferences(0).getBoolean(agentKey(owner),PromptAgent.enabled(agentTemplate(owner)));}
    private Button agentChip(VersionViewer owner){
        Button button=button("",Color.TRANSPARENT,TEXT);FlowDesign.symbol(button,"agent",UiLanguage.t("Modo Agente"));updateAgentChip(button,owner);
        button.setOnClickListener(v->{boolean on=!agentEnabled(owner);getPreferences(0).edit().putBoolean(agentKey(owner),on).apply();updateAgentChip(button,owner);
            if(owner!=null)owner.saveEditDraft();Toast.makeText(this,on?UiLanguage.t("Agente ativado: a LLM prepara o prompt antes de gerar."):UiLanguage.t("Agente desativado: prompt direto ao workflow."),Toast.LENGTH_SHORT).show();});
        button.setOnLongClickListener(v->{new FlowDialogs.Builder(this).setTitle(UiLanguage.t("Modo Agente")).setMessage(UiLanguage.t("Uma LLM local reescreve seu pedido para o modelo escolhido. O prompt original fica salvo.\n\nNSFW desligado: Qwen 3.8 27B.\nNSFW ligado: Qwen 3.8 27B Heretic.\n\nAs duas variantes usam as mesmas instruções por workflow e mantêm o text encoder original.")).setPositiveButton(UiLanguage.t("Entendi"),null).show();return true;});return button;
    }
    private void updateAgentChip(Button chip,VersionViewer owner){
        if(chip==null)return;boolean supported=PromptAgent.target(agentTemplate(owner))!=null,on=supported&&agentEnabled(owner);
        chip.setEnabled(supported);chip.setSelected(on);chip.setAlpha(supported?1f:.4f);
        chip.setBackground(FlowDesign.pill(this,on?0xFF3B3D41:FlowDesign.CONTROL,20,true));
        FlowDesign.symbol(chip,on?"agent-filled":"agent",UiLanguage.t("Modo Agente, ")+(supported?(on?UiLanguage.t("ativado"):UiLanguage.t("desativado")):UiLanguage.t("indisponível neste workflow")));
        if(android.os.Build.VERSION.SDK_INT>=26)chip.setTooltipText(chip.getContentDescription());
    }
    private void prepareAgent(JSONObject graph,JSONObject recipe,String address)throws Exception{
        SfwPromptFilter.strip(graph);
        String submittedPrompt=NegativePrompt.request(graph,SfwPromptFilter.clean(recipe.optString("prompt"),hideAllNsfw));
        JSONObject options=recipe.getJSONObject("options");boolean enabled=options.optBoolean("agent");
        JSONObject schema=enabled?ReferenceGraphs.schema(referenceJobs.server,address,"LlamaServerVisionCaption","UnloadAllModels","PreviewAny","PrimitiveStringMultiline","StringReplace","MomentosPromptGuard"):new JSONObject();
        PromptAgent.apply(graph,enabled,options.optBoolean("agentNsfw"),submittedPrompt,recipe.optString("operation"),schema);
        if(!enabled&&!NegativePrompt.value(graph).isEmpty())NegativePrompt.direct(graph);
        if(recipe.optString("operation").equals("image"))BoxLayout.apply(graph,options.optJSONObject("layout"),submittedPrompt);
        MemoryProfiles.apply(graph,options.optString("memoryProfile",memoryProfile()));
        PrivateConditioning.apply(graph,!hideAllNsfw);
        SfwPromptFilter.apply(graph,hideAllNsfw);
        WorkflowDependencies.verify(graph,ReferenceGraphs.schema(referenceJobs.server,address,WorkflowDependencies.types(graph)));
    }

    private String memoryProfile(){return MemoryProfiles.normalize(getPreferences(0).getString("memory_profile","24"));}
    private void buildMemorySettings(){
        if(memorySettings==null)return;memorySettings.removeAllViews();LinearLayout group=settingsSection(memorySettings,"memory",UiLanguage.t("Memória da GPU"),UiLanguage.t("Escolha a placa do servidor."));
        LinearLayout choices=row();choices.setPadding(dp(4),dp(4),dp(4),dp(4));choices.setBackground(round(BG,18,0));group.addView(choices);String current=memoryProfile();
        for(String profile:new String[]{"8","16","24","24plus"}){Button pick=button(profile.equals("24plus")?UiLanguage.t("24+ GB"):profile+UiLanguage.t(" GB"),profile.equals(current)?FIELD:Color.TRANSPARENT,profile.equals(current)?TEXT:MUTED);pick.setTextSize(12);pick.setPadding(0,0,0,0);pick.setSelected(profile.equals(current));pick.setContentDescription(UiLanguage.t("Perfil de memória ")+MemoryProfiles.label(profile));choices.addView(pick,new LinearLayout.LayoutParams(0,dp(44),1));pick.setOnClickListener(v->{getPreferences(0).edit().putString("memory_profile",profile).apply();buildMemorySettings();if(quickOptions!=null)rebuildQuickOptions();if(versionViewer!=null)versionViewer.rebuildOptions();});}
        gap(group,8);TextView detail=text(UiLanguage.t("Workflows incompatíveis ficam indisponíveis."),12,MUTED,false);group.addView(detail);
    }
    private JSONObject currentBoxLayout(){try{JSONObject layout=new JSONObject(getPreferences(0).getString("layout_"+mainDraftKey(),"{}"));return hideAllNsfw&&layout.optBoolean("private")?new JSONObject():layout;}catch(Exception e){return new JSONObject();}}
    private void openBoxLayout(){
        String ratio=currentRatio();if("Original".equals(ratio))ratio="1:1";String[] parts=ratio.split(":");double aspect=1;try{aspect=Double.parseDouble(parts[0])/Double.parseDouble(parts[1]);}catch(Exception ignored){}
        hideKeyboard(dockPrompt);BoxEditor.show(this,currentBoxLayout(),aspect,value->{getPreferences(0).edit().putString("layout_"+mainDraftKey(),value.toString()).apply();rebuildQuickOptions();});
    }
    private JSONObject bundledWorkflow(String asset)throws Exception{try(InputStream input=getAssets().open("workflows/"+asset)){return new JSONObject(new String(read(input,4_000_000),StandardCharsets.UTF_8));}}

    private void saveRecipe(String id,JSONObject draft,JSONObject graph,List<String> uploaded){
        try{GenerationRecipe.save(getFilesDir(),id,GenerationRecipe.finish(draft,graph,uploaded));}
        catch(Exception e){runOnUiThread(()->Toast.makeText(this,UiLanguage.t("Geração enviada, mas a configuração não pôde ser salva. Reutilização dependerá do histórico do servidor."),Toast.LENGTH_LONG).show());}
    }
    private JSONObject editTemplate(VersionViewer owner)throws Exception{
        JSONObject recipe=activeRecipe(owner);String operation=owner.extending?"video_extend":owner.editingVideo()?"video_edit":owner.animationEditor?"video":"image_edit";
        if(recipe!=null&&operation.equals(recipe.optString("operation"))&&(!owner.animationEditor||recipe.getJSONObject("options").optBoolean("turbo")== (owner.editingVideo()?owner.editTurbo:owner.turbo))){JSONObject graph=GenerationRecipe.configured(recipe);return operation.startsWith("video")?LoraSupport.videoLoader(graph):graph;}
        if(owner.extending)return videoGraph(owner.editTurbo);
        if(owner.editingVideo())try(InputStream in=getAssets().open("workflows/minimax_h3_video_edit_api.json")){JSONObject graph=new JSONObject(new String(read(in,1_000_000),StandardCharsets.UTF_8));graph.getJSONObject("11").getJSONObject("inputs").put("steps",owner.editTurbo?4:20);return graph;}
        return owner.animationEditor?videoGraph(owner.turbo):bundledWorkflow(ImageEditWorkflow.asset(!hideAllNsfw&&getPreferences(0).getBoolean("snofs_"+owner.draftKey(),true)));
    }

    private void applyReferenceAssets(JSONObject graph,List<ReferencePreset> presets,String address,List<String> explicit,List<Generation> sources)throws Exception{
        if(hideAllNsfw)LoraPrivacy.filterPresets(presets);
        boolean refVideo=ReferenceBindings.ref2va(graph);int capacity=refVideo?3:VideoWorkflow.matches(graph)?0:ReferenceSupport.capacity(graph);
        List<String> merged=new ArrayList<>(explicit);java.util.Set<String> sourceIds=new java.util.HashSet<>();for(Generation source:sources)sourceIds.add(source.id);
        if(refVideo){JSONObject input=graph.getJSONObject("8").getJSONObject("inputs");int occupied=0;for(Iterator<String> keys=input.keys();keys.hasNext();)if(keys.next().startsWith("ref_images."))occupied++;capacity-=occupied;merged.clear();}
        for(ReferencePreset.Picture image:ReferenceBindings.pictures(presets,refVideo,address)){
            if(merged.size()>=capacity)break;if(!image.source.isEmpty()&&sourceIds.contains(image.source))continue;
            File file=referenceStore.file(image.file);if(!file.exists())throw new IllegalArgumentException(UiLanguage.t("Imagem da referência indisponível. Revise a tag."));merged.add(uploadEditInput(address,file));if(!image.source.isEmpty())sourceIds.add(image.source);
        }
        if(refVideo)ReferenceBindings.bindH3Pictures(graph,merged);else if(capacity>0)ReferenceSupport.bind(graph,merged);
        boolean loras=false,mods=false;for(ReferencePreset p:presets){loras|=p.useLoras&&p.loras.length()>0;mods|=ReferenceBindings.validMod(p,address);}
        if(loras)ReferenceBindings.applyLoras(graph,presets,fetchLoras(address));
        if(refVideo&&mods)ReferenceBindings.applyMods(graph,presets,address,ReferenceGraphs.schema(referenceJobs.server,address,"MiniMaxH3RefModsLoader","MiniMaxH3RefModApply"));
        if(!presets.isEmpty()){
            StringBuilder names=new StringBuilder();for(ReferencePreset p:presets){if(names.length()>0)names.append(", ");names.append(p.prefix()).append(p.name);}
            for(Iterator<String> ids=graph.keys();ids.hasNext();){JSONObject node=graph.getJSONObject(ids.next());if(node.optString("class_type").startsWith("Save")){JSONObject meta=node.optJSONObject("_meta");if(meta==null)meta=new JSONObject();meta.put("momentos_references",names.toString());node.put("_meta",meta);}}
        }
    }

    private void prepareTaggedVideo(JSONObject graph,List<ReferencePreset> presets,boolean hasFrames,boolean turbo,String address)throws Exception{
        if(hasFrames||!ReferenceBindings.hasVisual(presets,address))return;
        // Reference images are subjects, not keyframes. Use the installed Ref2VA model for that mode.
        JSONObject reference;try(InputStream in=getAssets().open("workflows/minimax_h3_video_edit_api.json")){reference=new JSONObject(new String(read(in,1_000_000),StandardCharsets.UTF_8));}
        for(String id:new String[]{"4","19","20","21"})graph.put(id,reference.getJSONObject(id));
        graph.getJSONObject("21").getJSONObject("inputs").getJSONObject("lora_1").put("on",turbo);
        JSONObject input=graph.getJSONObject("8").getJSONObject("inputs");input.remove("first_frame");input.remove("last_frame");input.put("clip",new JSONArray().put("21").put(1)).put("ref_image_size","match");
        graph.getJSONObject("8").put("class_type","MiniMaxH3ReferenceToVideo");
        graph.getJSONObject("11").getJSONObject("inputs").put("model",ReferenceSupport.link("21"));graph.getJSONObject("12").getJSONObject("inputs").put("model",ReferenceSupport.link("21"));
    }

    private final class TagInput extends EditText {
        Runnable selectionChanged;
        TagInput(){super(MainActivity.this);}
        @Override protected void onSelectionChanged(int start,int end){super.onSelectionChanged(start,end);if(selectionChanged!=null)selectionChanged.run();}
    }

    private EditText activePrompt() {
        return versionViewer!=null&&versionViewer.dialog.isShowing()?versionViewer.instruction:dockPrompt;
    }

    private void insertTag(String tag) {
        EditText input=activePrompt();if(input==null)return;
        if(settingsDialog!=null&&settingsDialog.isShowing())settingsDialog.dismiss();
        int cursor=Math.max(0,input.getSelectionStart()),end=Math.max(cursor,input.getSelectionEnd());
        String before=input.getText().toString();int start=PromptTags.start(before,cursor);
        if(start>=0){cursor=start;end=PromptTags.end(before,end);}
        String prefix=cursor>0&&!Character.isWhitespace(before.charAt(cursor-1))?" ":"";
        String value=prefix+tag+" ";input.getText().replace(cursor,end,value);input.setSelection(cursor+value.length());
        input.requestFocus();input.postDelayed(()->{if(input.isAttachedToWindow()){input.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);}},180);
    }

    private void attachMentions(EditText input,LinearLayout composer){
        android.widget.HorizontalScrollView suggestions=new android.widget.HorizontalScrollView(this);suggestions.setHorizontalScrollBarEnabled(false);suggestions.setVisibility(View.GONE);
        LinearLayout list=row();suggestions.addView(list);composer.addView(suggestions,0,new LinearLayout.LayoutParams(-1,-2));
        Runnable refresh=()->{
            String value=input.getText().toString();int end=input.getSelectionStart(),start=PromptTags.start(value,end);list.removeAllViews();
            if(start<0||end!=input.getSelectionEnd()){suggestions.setVisibility(View.GONE);return;}
            char kind=value.charAt(start);String query=value.substring(start+1,end).toLowerCase(Locale.ROOT);int count=0;
            for(java.util.Map.Entry<String,String> entry:tagNames(kind).entrySet()){
                String tag=entry.getKey();if(!tag.substring(1).contains(query)&&!entry.getValue().toLowerCase(Locale.ROOT).contains(query))continue;
                Button choice=button(tag,FIELD,TEXT);FlowDesign.labelIcon(choice,kind=='@'?"person":kind=='$'?"style":kind=='&'?"object":"location");choice.setContentDescription(UiLanguage.t("Inserir ")+tag+" · "+entry.getValue());
                choice.setOnClickListener(v->{int cursor=input.getSelectionStart(),at=PromptTags.start(input.getText().toString(),cursor);if(at<0)return;int finish=PromptTags.end(input.getText().toString(),cursor);input.getText().replace(at,finish,tag+" ");input.setSelection(at+tag.length()+1);suggestions.setVisibility(View.GONE);});
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(44));cp.rightMargin=dp(6);list.addView(choice,cp);if(++count==8)break;
            }
            if(count==0){Button create=button(kind=='@'?UiLanguage.t("+ Novo personagem"):kind=='$'?UiLanguage.t("+ Novo estilo"):kind=='&'?UiLanguage.t("+ Novo objeto"):UiLanguage.t("+ Novo local"),FIELD,MUTED);
                create.setOnClickListener(v->{if(kind=='@')openCharacters();else if(kind=='$')openStyles();else if(kind=='&')openReferences(3);else openLocations();});list.addView(create,new LinearLayout.LayoutParams(-2,dp(44)));}
            suggestions.setVisibility(View.VISIBLE);
        };
        input.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void afterTextChanged(android.text.Editable value){refresh.run();}
            public void onTextChanged(CharSequence s,int start,int before,int count){}
        });
        if(input instanceof TagInput)((TagInput)input).selectionChanged=refresh;
    }

    private void loadCharacters() {
        selectedCharacterId = "";
        try {
            JSONArray saved = new JSONArray(getPreferences(0).getString("characters", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject entry = saved.getJSONObject(i);
                String id = entry.getString("id"), cover = entry.getString("cover");
                if (!validId(id) || !validId(cover)) continue;
                characters.add(new CharacterPreset(id, entry.getString("name"), entry.getString("description"),
                        cover, entry.optString("source"), entry.optBoolean("private")));
            }
        } catch (Exception e) { Toast.makeText(this, UiLanguage.t("Falha ao ler personagens."), Toast.LENGTH_LONG).show(); }
        if (selectedCharacter() == null) selectedCharacterId = "";
    }

    private CharacterPreset selectedCharacter() {
        for (CharacterPreset person : characters) if (person.id.equals(selectedCharacterId)) return person;
        return null;
    }

    private File characterCover(String id) {
        File directory = new File(getFilesDir(), "characters");
        if (!directory.exists()) directory.mkdirs();
        return new File(directory, id + ".jpg");
    }

    private boolean saveCharacters(List<CharacterPreset> updated, String selection) {
        try {
            JSONArray saved = new JSONArray();
            for (CharacterPreset person : updated) {
                JSONObject entry = new JSONObject();
                entry.put("id", person.id); entry.put("name", person.name); entry.put("description", person.description);
                entry.put("cover", person.coverId); entry.put("source", person.sourceId); entry.put("private", person.privateCover);
                saved.put(entry);
            }
            if (!getPreferences(0).edit().putString("characters", saved.toString())
                    .putString("selected_character", selection).commit()) throw new IllegalStateException();
            characters.clear(); characters.addAll(updated); selectedCharacterId = selection;
            updateCharacterButton();
            return true;
        } catch (Exception e) {
            Toast.makeText(this, UiLanguage.t("Falha ao salvar personagens."), Toast.LENGTH_LONG).show(); return false;
        }
    }

    private void updateCharacterButton() {
        if(characterButton==null)return;
        characterButton.setText(UiLanguage.t("Personagens · @"));characterButton.setTextColor(MUTED);FlowDesign.labelIcon(characterButton,"person");
        characterButton.setContentDescription(UiLanguage.t("Gerenciar personagens e inserir @tags"));
    }

    private Bitmap characterBitmap(CharacterPreset person) {
        Generation source = findGeneration(person.sourceId);
        if (person.privateCover || source != null && source.nsfw) {
            Generation safe = new Generation(); safe.id = person.id; safe.workflowName = "Personagens";
            return coverBitmap(safe);
        }
        return galleryThumbnail(characterCover(person.coverId));
    }

    private void openCharacters() {openReferences(1);}

    private void renderCharacters() {
        characterList.removeAllViews(); gap(characterList, 12);
        for (CharacterPreset person : characters) {
            gap(characterList, 10);
            LinearLayout row = row(); row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView cover = new ImageView(this); cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(round(FIELD, 12, 0)); cover.setClipToOutline(true);
            cover.setImageBitmap(characterBitmap(person)); cover.setContentDescription(UiLanguage.t("Capa de ") + person.name);
            View.OnClickListener select = v -> {
                charactersDialog.dismiss();insertTag("@"+stableHandle("character",person.id,person.name));
            };
            cover.setOnClickListener(select); row.addView(cover, new LinearLayout.LayoutParams(dp(76), dp(76)));
            Button choose = button("@"+stableHandle("character",person.id,person.name), Color.TRANSPARENT, TEXT);
            choose.setSingleLine(true); choose.setEllipsize(android.text.TextUtils.TruncateAt.END); choose.setOnClickListener(select);
            row.addView(choose, new LinearLayout.LayoutParams(0, dp(76), 1));
            Button options = button("⋮", Color.TRANSPARENT, MUTED);
            options.setContentDescription(UiLanguage.t("Opções do personagem ") + person.name);
            options.setOnClickListener(v -> {
                PopupMenu menu = new OrganicPopupMenu(this, options); menu.getMenu().add(UiLanguage.t("Editar")); menu.getMenu().add(UiLanguage.t("Apagar"));
                menu.setOnMenuItemClickListener(item -> {
                    if (UiLanguage.t("Editar").contentEquals(item.getTitle())) editCharacter(person, null);
                    else new FlowDialogs.Builder(this).setTitle(UiLanguage.t("Apagar personagem?"))
                            .setMessage(UiLanguage.t("Remove o personagem e sua capa. Gerações anteriores permanecem."))
                            .setNegativeButton(UiLanguage.t("Cancelar"), null).setPositiveButton(UiLanguage.t("Apagar"), (d, which) -> {
                                List<CharacterPreset> updated = new ArrayList<>(characters); updated.remove(person);
                                if (saveCharacters(updated, person.id.equals(selectedCharacterId) ? "" : selectedCharacterId)) {
                                    VaultFiles.delete(characterCover(person.coverId)); renderCharacters();
                                }
                            }).show();
                    return true;
                }); showIconMenu(menu);
            });
            row.addView(options, new LinearLayout.LayoutParams(dp(44), dp(48))); characterList.addView(row);
        }
        gap(characterList, 12);
    }

    private interface CoverSelection { void accept(Generation item); }

    private void pickCharacterCover(CoverSelection onPick) {
        List<Generation> images = new ArrayList<>();
        for (Generation item : generations) if (published(item)&&"done".equals(item.state) && thumbFile(item.id).exists()) images.add(item);
        if (images.isEmpty()) {
            Toast.makeText(this, UiLanguage.t("Gere uma imagem primeiro para usar como capa."), Toast.LENGTH_LONG).show(); return;
        }
        LinearLayout sheet = column(); sheet.setPadding(dp(16), dp(16), dp(16), dp(16));
        sheet.addView(text(UiLanguage.t("Escolher capa"), 22, TEXT, true));
        sheet.addView(text(UiLanguage.t("Imagens privadas aparecem cobertas."), 13, MUTED, false));
        android.widget.GridView grid = new android.widget.GridView(this);
        grid.setNumColumns(3); grid.setHorizontalSpacing(dp(4)); grid.setVerticalSpacing(dp(4));
        sheet.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        Button cancel = button(UiLanguage.t("Cancelar"), Color.TRANSPARENT, MUTED); sheet.addView(cancel);
        Dialog picker = styleSheet(sheet); cancel.setOnClickListener(v -> picker.dismiss());
        grid.setAdapter(new android.widget.BaseAdapter() {
            public int getCount() { return images.size(); }
            public Object getItem(int position) { return images.get(position); }
            public long getItemId(int position) { return position; }
            public View getView(int position, View recycled, android.view.ViewGroup parent) {
                Generation item = images.get(position);
                ImageView image = new ImageView(MainActivity.this);
                image.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1, dp(105)));
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setImageBitmap(item.nsfw ? coverBitmap(item) : galleryThumbnail(thumbFile(item.id)));
                image.setContentDescription(UiLanguage.t("Usar capa ") + (position + 1));
                image.setOnClickListener(v -> { onPick.accept(item); picker.dismiss(); }); return image;
            }
        });
    }

    private void editCharacter(CharacterPreset original, Generation initialCover) {
        ReferencePreset preset=original==null?new ReferencePreset():referenceStore.get(original.id);
        if(preset==null)preset=new ReferencePreset();
        if(initialCover!=null&&"image".equals(initialCover.mediaKind))try(InputStream input=VaultFiles.open(imageFile(initialCover.id))){
            ReferencePreset.Picture image=saveReferenceImage(input,initialCover.id,initialCover.nsfw);preset.images.add(image);preset.cover=image.file;preset.privateCover=initialCover.nsfw;
        }catch(Exception e){Toast.makeText(this,UiLanguage.t("Não foi possível abrir a imagem de referência."),Toast.LENGTH_LONG).show();}
        referenceStudio.edit(preset);
    }

    private void loadStyles() {
        selectedStyleId = "";
        try {
            JSONArray saved = new JSONArray(getPreferences(0).getString("prompt_styles", "[]"));
            for (int i = 0; i < saved.length(); i++) {
                JSONObject entry = saved.getJSONObject(i);
                promptStyles.add(new PromptStyle(entry.getString("id"), entry.getString("name"), entry.getString("text")));
            }
        } catch (Exception e) {
            promptStyles.clear();
            Toast.makeText(this, UiLanguage.t("Não foi possível ler os estilos salvos."), Toast.LENGTH_LONG).show();
        }
        if (selectedStyle() == null) selectedStyleId = "";
    }

    private PromptStyle selectedStyle() {
        for (PromptStyle style : promptStyles) if (style.id.equals(selectedStyleId)) return style;
        return null;
    }

    private boolean saveStyles(List<PromptStyle> updated, String selection) {
        try {
            JSONArray saved = new JSONArray();
            for (PromptStyle style : updated) {
                JSONObject entry = new JSONObject();
                entry.put("id", style.id); entry.put("name", style.name); entry.put("text", style.text);
                saved.put(entry);
            }
            if (!getPreferences(0).edit().putString("prompt_styles", saved.toString())
                    .putString("selected_prompt_style", selection).commit()) throw new IllegalStateException();
            promptStyles.clear();
            promptStyles.addAll(updated);
            selectedStyleId = selection;
            updateStyleButton();
            return true;
        } catch (Exception e) {
            Toast.makeText(this, UiLanguage.t("Falha ao salvar estilos. Tente novamente."), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void updateStyleButton() {
        if(styleButton==null)return;
        styleButton.setText(UiLanguage.t("Estilos · $"));styleButton.setTextColor(MUTED);FlowDesign.labelIcon(styleButton,"style");
        styleButton.setContentDescription(UiLanguage.t("Gerenciar estilos e inserir $tags"));
    }

    private void loadLocations() {
        try {
            JSONArray saved=new JSONArray(getPreferences(0).getString("prompt_locations","[]"));
            for(int i=0;i<saved.length();i++){JSONObject item=saved.getJSONObject(i);locations.add(new PromptStyle(item.getString("id"),item.getString("name"),item.getString("text")));}
        } catch(Exception e){Toast.makeText(this,UiLanguage.t("Não foi possível ler os locais salvos."),Toast.LENGTH_LONG).show();}
    }

    private boolean saveTextPresets(List<PromptStyle> updated,boolean places) {
        if(!places)return saveStyles(updated,"");
        try {
            JSONArray saved=new JSONArray();for(PromptStyle item:updated)saved.put(new JSONObject().put("id",item.id).put("name",item.name).put("text",item.text));
            if(!getPreferences(0).edit().putString("prompt_locations",saved.toString()).commit())throw new IllegalStateException();
            locations.clear();locations.addAll(updated);return true;
        } catch(Exception e){Toast.makeText(this,UiLanguage.t("Falha ao salvar locais."),Toast.LENGTH_LONG).show();return false;}
    }

    private Dialog styleSheet(LinearLayout content) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(content);
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(round(BG, 24, BORDER));
        dialog.getWindow().setGravity(Gravity.BOTTOM);
        dialog.getWindow().setLayout(-1, (int)(getResources().getDisplayMetrics().heightPixels * .68f));
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        return dialog;
    }

    private void openStyles() { openTextPresets(false); }
    private void openLocations() { openTextPresets(true); }

    private void openTextPresets(boolean places) {openReferences(places?2:0);}

    private void renderStyles() {
        styleList.removeAllViews();
        final boolean places=editingLocations;
        final List<PromptStyle> source=places?locations:promptStyles;
        for (PromptStyle style : source) {
            gap(styleList, 8);
            LinearLayout row = row();
            final String tag=(places?"#":"$")+stableHandle(places?"location":"style",style.id,style.name);
            Button choose = button(tag,FIELD,TEXT);FlowDesign.labelIcon(choose,places?"location":"style");
            choose.setContentDescription(UiLanguage.t("Inserir ")+tag+" · "+style.name);
            choose.setSingleLine(true);
            choose.setEllipsize(android.text.TextUtils.TruncateAt.END);
            choose.setOnClickListener(v -> {
                stylesDialog.dismiss();insertTag(tag);
            });
            row.addView(choose, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button edit = button("⋮", Color.TRANSPARENT, MUTED);
            edit.setContentDescription((places?UiLanguage.t("Opções do local "):UiLanguage.t("Opções do estilo ")) + style.name);
            row.addView(edit, new LinearLayout.LayoutParams(dp(48), dp(48)));
            edit.setOnClickListener(v -> {
                PopupMenu menu = new OrganicPopupMenu(this, edit);
                menu.getMenu().add(UiLanguage.t("Editar"));
                menu.getMenu().add(UiLanguage.t("Apagar"));
                menu.setOnMenuItemClickListener(item -> {
                    if (UiLanguage.t("Editar").contentEquals(item.getTitle())) editStyle(style);
                    else new FlowDialogs.Builder(this).setTitle(places?UiLanguage.t("Apagar local?"):UiLanguage.t("Apagar estilo?"))
                            .setMessage(UiLanguage.t("A tag será removida deste app. Gerações anteriores continuam iguais."))
                            .setNegativeButton(UiLanguage.t("Cancelar"), null)
                            .setPositiveButton(UiLanguage.t("Apagar"), (d, which) -> {
                                List<PromptStyle> updated = new ArrayList<>(source);
                                updated.remove(style);
                                if (saveTextPresets(updated,places)) renderStyles();
                            }).show();
                    return true;
                });
                showIconMenu(menu);
            });
            styleList.addView(row);
        }
        gap(styleList, 12);
    }

    private void editStyle(PromptStyle original) {
        ReferencePreset p=original==null?new ReferencePreset():referenceStore.get(original.id);
        if(p==null)p=new ReferencePreset();p.kind=editingLocations?"location":"style";referenceStudio.edit(p);
    }

    private void updateGenerationStatus() {
        if (generationProgress == null) return;
        boolean busy = !runButton.isEnabled();
        for (Generation item : generations)
            busy |= "queued".equals(item.state) || "running".equals(item.state);
        generationProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private final class GenerationPulse extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint();
        GenerationPulse() { super(MainActivity.this); }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float width = Math.max(1, getWidth());
            float phase = (android.os.SystemClock.uptimeMillis() % 2200) / 2200f;
            float x = (phase * 2 - .5f) * width;
            paint.setShader(new android.graphics.LinearGradient(x - width * .5f, 0, x + width * .5f, getHeight(),
                    new int[]{0xFF22252D, 0xFF3E4B42, 0xFF22252D}, null, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, width, getHeight(), paint);
            if (isShown() && getWindowVisibility() == View.VISIBLE) postInvalidateOnAnimation();
        }
    }

    private void hideGalleryReveal() {
        if (revealedGridImage != null) {revealedGridImage.setImageBitmap(revealedGridCover);revealedGridImage.setScaleType(ImageView.ScaleType.CENTER_CROP);}
        revealedGridImage = null;
        revealedGridCover = null;
        updateMainPrivacy();
    }

    private double galleryAspect(Generation item) {
        if(item.mediaWidth<=0||item.mediaHeight<=0){
            File file=thumbFile(item.id);
            if(file.exists()){BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;VaultFiles.bitmap(file.getAbsolutePath(),bounds);
                if(bounds.outWidth>0&&bounds.outHeight>0){item.mediaWidth=bounds.outWidth;item.mediaHeight=bounds.outHeight;}}
        }
        if(item.mediaWidth>0&&item.mediaHeight>0)return (double)item.mediaWidth/item.mediaHeight;
        double requested=GenerationAspect.requested(item.settings);if(requested>0)return requested;
        Generation source=findGeneration(item.parentId.isEmpty()?item.sourceId:item.parentId);
        if(source!=null&&source.mediaWidth>0&&source.mediaHeight>0)return (double)source.mediaWidth/source.mediaHeight;
        return 1;
    }

    private View galleryTile(Generation item) {
        android.widget.FrameLayout tile = new android.widget.FrameLayout(this);
        tile.setBackground(round(FIELD, 16, 0));
        tile.setClipToOutline(true);
        File file = thumbFile(item.id);
        if (!"done".equals(item.state) || !file.exists()) {
            if ("queued".equals(item.state) || "running".equals(item.state))
                tile.addView(new GenerationPulse(), new android.widget.FrameLayout.LayoutParams(-1, -1));
            TextView state = text("error".equals(item.state) ? UiLanguage.t("Falhou") : "done".equals(item.state)
                    ? UiLanguage.t("Indisponível") : UiLanguage.t("Gerando…"), 12, MUTED, false);
            state.setGravity(Gravity.CENTER);
            tile.addView(("queued".equals(item.state)||"running".equals(item.state))?new GenerationProgressView(item,true):state,new android.widget.FrameLayout.LayoutParams(-1,-1));
            tile.setOnClickListener(v->openViewer(item));decorateSetTile(tile,item);
            return tile;
        }
        ImageView photo = new ImageView(this);
        photo.setScaleType(covered(item)?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_CENTER);
        photo.setContentDescription(UiLanguage.t("Abrir conjunto ")+item.workflowName+" · "+versionName(item));
        tile.addView(photo, new android.widget.FrameLayout.LayoutParams(-1, -1));
        if (covered(item)) {
            Bitmap cover = coverBitmap(item);
            photo.setImageBitmap(cover);
            photo.setOnLongClickListener(v -> {
                if (hideAllNsfw) return true;
                hideGalleryReveal();
                Bitmap actual = galleryThumbnail(file);
                if (actual == null) return true;
                revealedGridImage = photo;
                revealedGridCover = cover;
                photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                photo.setImageBitmap(actual);
                return true;
            });
            photo.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_UP
                        || event.getActionMasked() == MotionEvent.ACTION_CANCEL) hideGalleryReveal();
                return false;
            });
            photo.setOnClickListener(v -> openViewer(item));
        } else {
            photo.setImageBitmap(galleryThumbnail(file));
            photo.setOnClickListener(v -> openViewer(item));
        }
        decorateSetTile(tile,item);
        if ("video".equals(item.mediaKind)) {
            if(!covered(item)&&videoFile(item.id).isFile()&&!selecting){GalleryVideoPreview preview=new GalleryVideoPreview(this,videoFile(item.id));tile.addView(preview,1,new android.widget.FrameLayout.LayoutParams(-1,-1));tile.setTag(preview);preview.setOnClickListener(v->openViewer(item));}
            ImageView badge=new ImageView(this);badge.setImageDrawable(FlowDesign.icon(this,"play",16));badge.setPadding(dp(6),dp(6),dp(6),dp(6));badge.setBackground(round(0xCC151719,10,0));badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            android.widget.FrameLayout.LayoutParams badgePosition=new android.widget.FrameLayout.LayoutParams(dp(28),dp(28),Gravity.LEFT|Gravity.TOP);badgePosition.setMargins(dp(8),dp(8),0,0);tile.addView(badge,badgePosition);
        }
        return tile;
    }

    private List<Generation> visibleVersions(String root) {
        List<Generation> members=versions(root);members.removeIf(item->!published(item));return members;
    }

    private void rebuildGallerySets() {
        gallerySets.clear();gallerySets.addAll(GallerySets.covers(generations,new GallerySets.Access<Generation>(){
            public String id(Generation item){return item.id;}
            public String root(Generation item){return familyId(item);}
            public boolean visible(Generation item){return published(item)&&(!(selectedTab==3&&!activeProjectId.isEmpty())||familyInProject(item,activeProjectId));}
            public boolean ready(Generation item){return "done".equals(item.state)&&thumbFile(item.id).exists();}
            public long time(Generation item){return item.createdAt;}
            public String preferred(String root){return getPreferences(0).getString("gallery_cover_"+root,"");}
        }));
    }

    private void decorateSetTile(android.widget.FrameLayout tile,Generation item){
        int count=visibleVersions(familyId(item)).size();
        if(count>1){TextView badge=text(String.valueOf(count),12,TEXT,false);badge.setGravity(Gravity.CENTER);badge.setIncludeFontPadding(false);badge.setCompoundDrawables(FlowDesign.icon(this,"layers",16),null,null,null);badge.setCompoundDrawablePadding(dp(4));badge.setPadding(dp(8),0,dp(8),0);badge.setBackground(round(0xCC151719,10,0));
            android.widget.FrameLayout.LayoutParams bp=new android.widget.FrameLayout.LayoutParams(-2,dp(28),Gravity.TOP|Gravity.RIGHT);bp.setMargins(0,dp(8),dp(8),0);tile.addView(badge,bp);}
        if(selecting){addSelectionOverlay(tile,item);return;}
        if(item.prompt!=null&&!item.prompt.trim().isEmpty()){
            Button reuse=galleryAction("reuse","Reutilizar prompt da capa do conjunto");
            android.widget.FrameLayout.LayoutParams rp=new android.widget.FrameLayout.LayoutParams(dp(44),dp(44),Gravity.LEFT|Gravity.BOTTOM);rp.setMargins(dp(4),0,0,dp(4));tile.addView(reuse,rp);
            reuse.setOnClickListener(v->{rebuildGallerySets();for(Generation cover:gallerySets)if(familyId(cover).equals(familyId(item))){reusePrompt(cover);return;}});
        }
        Button options=galleryAction("more","Opções do conjunto "+familyId(item));
        android.widget.FrameLayout.LayoutParams p=new android.widget.FrameLayout.LayoutParams(dp(44),dp(44),Gravity.RIGHT|Gravity.BOTTOM);p.setMargins(0,0,dp(4),dp(4));tile.addView(options,p);
        options.setOnClickListener(v->showLibraryActions(options,item,true));
    }

    private Button galleryAction(String symbol,String description){
        Button action=button("",Color.TRANSPARENT,TEXT);action.setMinHeight(0);action.setMinimumHeight(0);FlowDesign.symbol(action,symbol,description);
        android.graphics.drawable.Drawable surface=new android.graphics.drawable.InsetDrawable(round(0xD9151719,18,0x18FFFFFF),dp(4));
        android.graphics.drawable.Drawable mask=new android.graphics.drawable.InsetDrawable(round(Color.WHITE,18,0),dp(4));
        action.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x30FFFFFF),surface,mask));return action;
    }

    private boolean canDelete(Generation item) {
        return !editUploads.contains(item.id) && ("done".equals(item.state) || "error".equals(item.state));
    }

    private void updateGalleryActions() {
        if (galleryActions == null) return;
        if (generationDock != null) generationDock.setVisibility(selecting||selectedTab==4||selectedTab==3&&activeProjectId.isEmpty() ? View.GONE : View.VISIBLE);
        galleryActions.removeAllViews();
        if (!selecting) {
            ProjectAlbum currentProject=selectedTab==3?project(activeProjectId):null;
            if(currentProject!=null){Button back=button("",Color.TRANSPARENT,TEXT);FlowDesign.symbol(back,"left",UiLanguage.t("Voltar aos projetos"));galleryActions.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));back.setOnClickListener(v->{saveDraft();activeProjectId="";selectTab(3,false);});TextView title=text(currentProject.name,15,TEXT,true);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);galleryActions.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));title.setGravity(Gravity.CENTER_VERTICAL);
                Button add=button("",Color.TRANSPARENT,TEXT);FlowDesign.symbol(add,"plus",UiLanguage.t("Adicionar gerações ao projeto"));galleryActions.addView(add,new LinearLayout.LayoutParams(dp(44),dp(48)));add.setOnClickListener(v->projectPicker(currentProject,false));Button more=button("⋮",Color.TRANSPARENT,TEXT);more.setContentDescription(UiLanguage.t("Opções do projeto ")+currentProject.name);galleryActions.addView(more,new LinearLayout.LayoutParams(dp(44),dp(48)));more.setOnClickListener(v->projectMenu(more,currentProject));return;}
            TextView count = text(gallerySets.size() + (gallerySets.size()==1?UiLanguage.t(" mídia"):UiLanguage.t(" mídias")), 13, MUTED, false);
            count.setGravity(Gravity.CENTER_VERTICAL);
            galleryActions.addView(count, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button reference = button(UiLanguage.t("+ Imagem"), Color.TRANSPARENT, TEXT);
            reference.setContentDescription(UiLanguage.t("Importar imagens"));
            reference.setOnClickListener(v -> pickReference(false));
            FlowDesign.symbol(reference,"image",UiLanguage.t("Importar imagens"));galleryActions.addView(reference,new LinearLayout.LayoutParams(dp(48),dp(48)));
            Button select = button(UiLanguage.t("Selecionar"), Color.TRANSPARENT, TEXT);
            select.setOnClickListener(v -> {
                hideGalleryReveal();
                ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(dockPrompt.getWindowToken(), 0);
                selecting = true;
                selectedGenerations.clear();
                updateGalleryActions();
                galleryAdapter.notifyDataSetChanged();
            });
            FlowDesign.symbol(select,"select",UiLanguage.t("Selecionar vários itens"));galleryActions.addView(select,new LinearLayout.LayoutParams(dp(48),dp(48)));
            return;
        }
        java.util.Set<String> eligible=new java.util.HashSet<>();
        for(Generation item:gallerySets)if(canDeleteSet(item))eligible.addAll(GenerationStorage.familyIds(generations,java.util.Collections.singleton(item.id)));
        final boolean all = !eligible.isEmpty() && selectedGenerations.containsAll(eligible);
        Button selectAll = button(all ? UiLanguage.t("Limpar") : UiLanguage.t("Todas"), Color.TRANSPARENT, TEXT);
        selectAll.setOnClickListener(v -> {
            selectedGenerations.clear();
            if (!all) selectedGenerations.addAll(eligible);
            updateGalleryActions(); galleryAdapter.notifyDataSetChanged();
        });
        galleryActions.addView(selectAll, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button delete = button(UiLanguage.t("Apagar (") + selectedGenerations.size() + ")", FIELD, TEXT);
        delete.setEnabled(!selectedGenerations.isEmpty());
        delete.setAlpha(delete.isEnabled() ? 1f : .45f);
        delete.setOnClickListener(v -> confirmDeleteSelected());
        galleryActions.addView(delete, new LinearLayout.LayoutParams(0, dp(48), 1.4f));
        Button cancel = button(UiLanguage.t("Cancelar"), Color.TRANSPARENT, MUTED);
        cancel.setOnClickListener(v -> {
            selecting = false; selectedGenerations.clear();
            updateGalleryActions(); galleryAdapter.notifyDataSetChanged();
        });
        galleryActions.addView(cancel, new LinearLayout.LayoutParams(0, dp(48), 1));
    }

    private void addSelectionOverlay(android.widget.FrameLayout tile, Generation item) {
        if (!selecting) return;
        List<Generation> eligible=canDeleteSet(item)?versions(familyId(item)):new ArrayList<>();
        boolean selected=!eligible.isEmpty();for(Generation member:eligible)selected &= selectedGenerations.contains(member.id);
        final boolean allSelected=selected;
        TextView overlay = text(!eligible.isEmpty() ? (selected ? "✓" : "○") : UiLanguage.t("Em andamento"), 20, TEXT, true);
        overlay.setGravity(Gravity.TOP | Gravity.RIGHT);
        overlay.setPadding(dp(8), dp(8), dp(8), dp(8));
        overlay.setBackground(round(selected ? 0x553F6032 : 0x11000000, 6, selected ? ACCENT : 0));
        overlay.setContentDescription((selected ? UiLanguage.t("Desmarcar ") : UiLanguage.t("Selecionar ")) + item.id);
        overlay.setOnClickListener(v -> {
            for(Generation member:eligible){if(allSelected)selectedGenerations.remove(member.id);else selectedGenerations.add(member.id);}
            updateGalleryActions(); galleryAdapter.notifyDataSetChanged();
        });
        tile.addView(overlay, new android.widget.FrameLayout.LayoutParams(-1, -1));
    }

    private void confirmDeleteSelected() {
        final java.util.Set<String> ids = new java.util.HashSet<>(selectedGenerations);
        confirmDeleteSets(ids);
    }
    private boolean canDeleteSet(Generation item){
        for(Generation member:versions(familyId(item)))if(!canDelete(member))return false;
        return true;
    }
    private void confirmDeleteSets(java.util.Set<String> selected){
        java.util.Set<String> ids=GenerationStorage.familyIds(generations,selected);if(ids.isEmpty())return;
        for(Generation member:generations)if(ids.contains(member.id)&&!canDelete(member)){Toast.makeText(this,UiLanguage.t("O conjunto ainda tem gerações em andamento. Aguarde concluir ou use Sanitize para limpar tudo."),Toast.LENGTH_LONG).show();return;}
        new FlowDialogs.Builder(this).setTitle(UiLanguage.t("Apagar ")+ids.size()+UiLanguage.t(" itens de todo o app?"))
            .setMessage(UiLanguage.t("Apaga todas as versões dos conjuntos selecionados, inclusive ocultas, no Feed, Galeria, Prompter e Projetos. Referências cadastradas e arquivos do ComfyUI permanecem. Não é possível desfazer."))
            .setNegativeButton(UiLanguage.t("Cancelar"),null).setPositiveButton(UiLanguage.t("Apagar"),(d,w)->runStorageAction(selected,true,false,false)).show();
    }

    private Bitmap galleryThumbnail(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        VaultFiles.bitmap(file.getAbsolutePath(), options);
        options.inJustDecodeBounds = false;
        options.inSampleSize = Math.max(1, Math.max(options.outWidth, options.outHeight) / 384);
        return VaultFiles.bitmap(file.getAbsolutePath(), options);
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        return view;
    }

    private LinearLayout card() {
        LinearLayout view = column();
        view.setPadding(dp(20), dp(20), dp(20), dp(20));
        view.setBackground(round(FlowDesign.SURFACE, 24, 0));
        return view;
    }

    private GradientDrawable glass(int radius) {
        GradientDrawable surface = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF292C32, 0xFF22242A});
        surface.setCornerRadius(dp(radius));
        surface.setStroke(dp(1), 0x12FFFFFF);
        return surface;
    }

    private GradientDrawable round(int fill, int radius, int stroke) {
        return Appearance.surface(this,fill,radius,stroke);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(bold||value.length()<90?Branding.secondary(this,bold?600:500):Branding.body(this,400));view.setIncludeFontPadding(false);
        return view;
    }

    private Button button(String value, int fill, int color) {
        Button view = new FlowDesign.IconButton(this);
        view.setAllCaps(false);
        view.setMinWidth(0);
        view.setMinimumWidth(0);
        view.setStateListAnimator(null);
        view.setText(value);
        view.setTextSize(13);
        view.setTextColor(color);
        view.setTypeface(Branding.secondary(this,600));view.setIncludeFontPadding(false);
        view.setBackground(FlowDesign.pill(this,fill,20,false));
        view.setMinHeight(dp(48));
        view.setPadding(dp(14), dp(5), dp(14), dp(5));
        return view;
    }

    private void styleInput(EditText edit) {
        edit.setTextColor(TEXT);
        edit.setHintTextColor(MUTED);
        edit.setTextSize(15);edit.setTypeface(Branding.body(this,400));
        edit.setPadding(dp(13), dp(11), dp(13), dp(11));
        edit.setBackground(round(FlowDesign.SURFACE, 16, BORDER));
    }

    private void gap(LinearLayout parent, int height) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private String checkedUrl() {
        String value = serverInput.getText().toString().trim().replaceAll("/+$", "");
        if(value.isEmpty()||!value.equals(DesktopConnection.endpoint(this)))throw new IllegalArgumentException(UiLanguage.t("Pareie com bwdacc Desktop em Ajustes > Conexão."));
        URL url;
        try { url = new URL(value); }
        catch (Exception e) { throw new IllegalArgumentException(UiLanguage.t("Endereço inválido. Use http://IP:porta.")); }
        if (!("http".equals(url.getProtocol()) || "https".equals(url.getProtocol()))
                || url.getHost().isEmpty() || !url.getPath().isEmpty() && !"/".equals(url.getPath())
                || url.getUserInfo() != null || url.getQuery() != null || url.getRef() != null) {
            throw new IllegalArgumentException(UiLanguage.t("Use só endereço base: http://IP:porta"));
        }
        return value;
    }

    private void connect() {
        try { baseUrl = checkedUrl(); }
        catch (Exception e) {
            connected = false;
            connection.setText(e.getMessage());
            workflowDetails.setVisibility(View.VISIBLE); connectionPanel.setVisibility(View.VISIBLE);
            return;
        }
        stopProgressSocket();
        connected = false;
        connection.setText(UiLanguage.t("● Conectando"));
        connection.setTextColor(MUTED);
        final String address = baseUrl;
        for (Generation item : generations) if (item.serverAddress.isEmpty() && ("queued".equals(item.state) || "running".equals(item.state)))
            item.serverAddress = getPreferences(0).getString("server", address);
        saveFeed();
        if (!polling) { polling = true; poll(); }
        worker.execute(() -> {
            try {
                getJson(address + "/system_stats");
                runOnUiThread(() -> {
                    if (!address.equals(baseUrl)) return;
                    connected = true;
                    getPreferences(0).edit().putString("server", address).apply();
                    connection.setText(UiLanguage.t("● Conectado"));
                    connection.setTextColor(MINT);
                    connectionPanel.setVisibility(View.GONE);
                    if (!polling) { polling = true; poll(); }
                    refreshPending();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!address.equals(baseUrl)) return;
                    connected = false;
                    connection.setText(UiLanguage.t("● Offline"));
                    connection.setTextColor(MUTED);
                    workflowDetails.setVisibility(View.VISIBLE); connectionPanel.setVisibility(View.VISIBLE);
                    result.setText(UiLanguage.t("Conexão falhou: ") + e.getMessage());
                });
            }
        });
    }

    @Override protected void onActivityResult(int request, int code, Intent data) {
        super.onActivityResult(request, code, data);
        if(referenceStudio!=null&&referenceStudio.audio.result(request,code,data))return;
        if(request==PICK_LIPSYNC_AUDIO){LipsyncSheet target=pickingLipsync;pickingLipsync=null;if(target!=null&&code==RESULT_OK&&data!=null&&data.getData()!=null)target.picked(data.getData());return;}
        if(request==PAIR_DESKTOP){if(code==RESULT_OK){for(int i=0;i<connectionPanel.getChildCount();i++)connectionPanel.getChildAt(i).setVisibility(View.VISIBLE);serverInput.setText(DesktopConnection.endpoint(this));serverInput.setVisibility(View.GONE);connectionMode.setText(DesktopConnection.description(this));connect();}return;}
        if(request==PICK_PRESET_IMAGES){if(code==RESULT_OK&&data!=null)importPresetImages(data);else pendingPresetPick=null;return;}
        if(request==PICK_PROJECT_COVER){if(code==RESULT_OK&&data!=null&&data.getData()!=null)importProjectCover(data.getData());return;}
        if (request == PICK_ANIMATION_IMAGE) {
            if (code == RESULT_OK && data != null) importReferenceImages(data);
            return;
        }
        if (request == PICK_COVERS) {
            if (code == RESULT_OK && data != null) importCovers(data);
            return;
        }
        if (request != PICK_WORKFLOW || code != RESULT_OK || data == null || data.getData() == null) return;
        try (InputStream input = getContentResolver().openInputStream(data.getData())) {
            byte[] content = read(input, 4_000_000);
            JSONObject parsed = new JSONObject(new String(content, StandardCharsets.UTF_8));
            validateWorkflow(parsed);
            String name = "Workflow";
            try (Cursor cursor = getContentResolver().query(data.getData(),
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
            }
            if (name.toLowerCase(Locale.ROOT).endsWith(".json"))
                name = name.substring(0, name.length() - 5);
            activateWorkflow(parsed, name);
        } catch (Exception e) {
            result.setText(UiLanguage.t("Importação falhou: ") + e.getMessage());
        }
    }

    private void writeWorkflow(File path, JSONObject value) throws Exception {
        VaultFiles.write(path,value.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void rememberWorkflow(JSONObject graph, String name) throws Exception {
        File directory = new File(getFilesDir(), "workflows");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException(UiLanguage.t("Não foi possível salvar workflow."));
        String id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
        writeWorkflow(new File(directory, id + ".json"), new JSONObject().put("name", name).put("prompt", graph));
    }

    private void activateWorkflow(JSONObject graph, String name) throws Exception {
        validateWorkflow(graph); saveDraft();
        if (workflow != null) rememberWorkflow(workflow, workflowName);
        rememberWorkflow(graph, name);
        writeWorkflow(new File(getFilesDir(), "workflow.json"), graph);
        workflow = graph; workflowName = name;
        getPreferences(0).edit().remove(reuseKey(null)).apply();
        if(!VideoWorkflow.matches(graph))getPreferences(0).edit().putString("last_image_workflow",name).apply();
        getPreferences(0).edit().putString("workflow_name", name).apply();
        workflowNameView.setText(name); showFields();
        updateCoverCount(); result.setText(UiLanguage.t("Workflow selecionado. Dependências serão verificadas ao gerar.")); refreshPending(); showTab(false);
    }

    private void chooseWorkflow() {
        final List<String> names = new ArrayList<>(); final List<JSONObject> graphs = new ArrayList<>();
        try {
            if (workflow != null) rememberWorkflow(workflow, workflowName);
            try(InputStream input=getAssets().open("workflows/catalog.json")){
                JSONArray catalog=new JSONObject(new String(read(input,1_000_000),StandardCharsets.UTF_8)).getJSONArray("workflows");
                for(int i=0;i<catalog.length();i++){JSONObject entry=catalog.getJSONObject(i);names.add(entry.getString("name"));graphs.add(bundledWorkflow(entry.getString("base_graph")));}
            }
            names.add(ImageEditWorkflow.KLEIN);graphs.add(bundledWorkflow(ImageEditWorkflow.asset(false)));
            if(!hideAllNsfw){names.add(ImageEditWorkflow.SNOFS);graphs.add(bundledWorkflow(ImageEditWorkflow.asset(true)));}
            File[] saved = new File(getFilesDir(), "workflows").listFiles((dir, name) -> name.endsWith(".json"));
            if (saved != null) {
                java.util.Arrays.sort(saved, (a, b) -> a.getName().compareTo(b.getName()));
                for (File file : saved) {
                    try (InputStream input = VaultFiles.open(file)) {
                        JSONObject entry = new JSONObject(new String(read(input, 4_000_000), StandardCharsets.UTF_8));
                        String name = entry.getString("name"); JSONObject graph = entry.getJSONObject("prompt");
                        validateWorkflow(graph);
                        if (!names.contains(name)) { names.add(name); graphs.add(graph); }
                    } catch (Exception ignored) { }
                }
            }
        } catch (Exception e) { Toast.makeText(this, UiLanguage.t("Falha ao abrir workflows: ") + e.getMessage(), Toast.LENGTH_LONG).show(); return; }
        WorkflowPicker.show(this,names,graphs,workflowName,VideoWorkflow.matches(workflow),memoryProfile(),position->{
            String prompt=dockPrompt.getText().toString();List<String> refs=new ArrayList<>(mainReferences.ids);
            try {activateWorkflow(graphs.get(position),names.get(position));mainReferences.replace(refs);dockPrompt.setText(prompt);saveDraft();if(settingsDialog!=null)settingsDialog.dismiss();rebuildQuickOptions();}
            catch(Exception e){Toast.makeText(this,UiLanguage.t("Falha ao trocar workflow: ")+e.getMessage(),Toast.LENGTH_LONG).show();}
        },()->{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("application/json");intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,PICK_WORKFLOW);});
    }



    private void restoreWorkflow() {
        try (InputStream input = VaultFiles.open(new File(getFilesDir(),"workflow.json"))) {
            workflow = new JSONObject(new String(read(input, 4_000_000), StandardCharsets.UTF_8));
            validateWorkflow(workflow);
            workflowName = getPreferences(0).getString("workflow_name", "Workflow");
            workflowNameView.setText(workflowName);
            showFields();
            updateCoverCount();
        } catch (Exception ignored) { workflow = null; }
    }

    private void importCovers(Intent data) {
        if (workflow == null) return;
        List<Uri> selected = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) selected.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) selected.add(data.getData());
        int imported = 0;
        for (Uri uri : selected) {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                byte[] bytes = read(input, 10_000_000);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (options.outWidth <= 0 || options.outHeight <= 0) continue;
                options.inJustDecodeBounds = false;
                options.inSampleSize = Math.max(1,
                        (Math.max(options.outWidth, options.outHeight) + 1599) / 1600);
                Bitmap cover = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                if (cover == null) continue;
                File directory = coverDir(workflowName);
                if (!directory.exists() && !directory.mkdirs()) {
                    cover.recycle();
                    continue;
                }
                File file = new File(directory, UUID.randomUUID().toString() + ".jpg");
                try (OutputStream output = VaultFiles.output(file)) {
                    cover.compress(Bitmap.CompressFormat.JPEG, 88, output);
                } finally { cover.recycle(); }
                imported++;
            } catch (Exception ignored) { }
        }
        if (imported > 0) getPreferences(0).edit().putBoolean("custom_covers_" + workflowName, true).apply();
        coverCache.evictAll();
        updateCoverCount();
        renderFeed();
        result.setText(imported == 0 ? UiLanguage.t("Nenhuma capa válida selecionada.")
                : imported + (imported == 1 ? UiLanguage.t(" capa adicionada.") : UiLanguage.t(" capas adicionadas.")));
    }

    private File coverDir(String name) {
        String key = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
        return new File(new File(getFilesDir(), "covers"), key);
    }

    private File[] customCoverFiles(String name) {
        File[] files = coverDir(name).listFiles(File::isFile);
        if (files == null) return new File[0];
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        return files;
    }

    private void updateCoverCount() {
        if (coverCount == null) return;
        int count = getPreferences(0).getBoolean("custom_covers_" + workflowName, false)
                ? customCoverFiles(workflowName).length : 0;
        coverCount.setText(count == 0 ? UiLanguage.t("3 capas padrão de reserva")
                : count + (count == 1 ? UiLanguage.t(" capa de reserva deste workflow") : UiLanguage.t(" capas de reserva deste workflow")));
    }

    private void validateWorkflow(JSONObject object) throws Exception {
        if (object.length() == 0) throw new IllegalArgumentException(UiLanguage.t("Workflow vazio."));
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            JSONObject node = object.optJSONObject(keys.next());
            if (node == null || !node.has("class_type") || node.optJSONObject("inputs") == null)
                throw new IllegalArgumentException(UiLanguage.t("Exporte no formato API do ComfyUI."));
        }
    }

    private String primaryLabel(String type, String name, Object value) {
        if ("PrimitiveStringMultiline".equals(type) && "value".equals(name)
                && String.valueOf(value).length() < 500) return "Ideia da imagem";
        if ("ResolutionSelector".equals(type) && "aspect_ratio".equals(name)) return "Proporção";
        if ("ResolutionSelector".equals(type) && "megapixels".equals(name)) return "Megapixels";
        return null;
    }

    private int primaryOrder(String name) {
        if ("Ideia da imagem".equals(name)) return 0;
        if ("Proporção".equals(name)) return 1;
        if ("Megapixels".equals(name)) return 2;
        return 3;
    }

    private void showFields() {
        updateLoraButton();
        bindingFields = true;
        updateComposerChip();
        fieldList.removeAllViews();
        advancedList.removeAllViews();
        fields.clear();
        dockPrompt.setText("");
        dockPrompt.setEnabled(false);
        dockPrompt.setHint(UiLanguage.t("Escolha seu workflow em Ajustes"));
        if (workflow == null) { bindingFields = false; return; }
        List<Field> primary = new ArrayList<>();
        List<Field> advanced = new ArrayList<>();
        String explicitPrompt="";Iterator<String> marked=workflow.keys();while(marked.hasNext()){String id=marked.next();JSONObject meta=workflow.optJSONObject(id).optJSONObject("_meta");if(meta!=null&&meta.has("momentos_prompt_input")){explicitPrompt=id+"/"+meta.optString("momentos_prompt_input");break;}}
        Iterator<String> ids = workflow.keys();
        while (ids.hasNext()) {
            String id = ids.next();
            JSONObject node = workflow.optJSONObject(id);
            if (node == null) continue;
            JSONObject inputs = node.optJSONObject("inputs");
            if (inputs == null) continue;
            Iterator<String> names = inputs.keys();
            while (names.hasNext()) {
                String name = names.next();
                Object value = inputs.opt(name);
                if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) continue;
                Field field = new Field();
                field.nodeId = id;
                field.key = name;
                field.original = value;
                field.label = node.optString("class_type") + " · " + name + " (#" + id + ")";
                field.primaryLabel = (VideoWorkflow.matches(workflow)&&"8".equals(id)&&"prompt".equals(name))||(ReferenceSupport.klein(workflow)&&"6".equals(id)&&"text".equals(name))?"Ideia da imagem":primaryLabel(node.optString("class_type"), name, value);
                if(!explicitPrompt.isEmpty()){if(explicitPrompt.equals(id+"/"+name))field.primaryLabel="Ideia da imagem";else if("Ideia da imagem".equals(field.primaryLabel))field.primaryLabel=null;}
                fields.add(field);
                if (field.primaryLabel == null) advanced.add(field);
                else primary.add(field);
            }
        }
        if (primary.isEmpty()) { primary.addAll(advanced); advanced.clear(); }
        Collections.sort(primary, (a, b) -> Integer.compare(
                primaryOrder(a.primaryLabel), primaryOrder(b.primaryLabel)));
        boolean boundPrompt = false;
        for (Field field : primary) {
            if ("Ideia da imagem".equals(field.primaryLabel)) {
                if (boundPrompt) field.primaryLabel = null;
                else boundPrompt = true;
            }
        }
        for (Field field : primary) addField(fieldList, field, field.primaryLabel == null ? field.label : field.primaryLabel);
        for (Field field : advanced) addField(advancedList, field, field.label);
        advancedButton.setVisibility(advanced.isEmpty() ? View.GONE : View.VISIBLE);
        if (fields.isEmpty()) fieldList.addView(text(UiLanguage.t("Nenhum campo simples editável."), 14, MUTED, false));
        if (hasIdeaField() && getPreferences(0).contains(mainDraftKey()))
            dockPrompt.setText(getPreferences(0).getString(mainDraftKey(), ""));
        String savedRatio=getPreferences(0).getString("ratio_"+workflowName,"");
        for(Field field:fields)if("Proporção".equals(field.primaryLabel)&&field.spinner!=null)
            for(int i=0;i<RATIOS.length;i++)if(RATIOS[i].startsWith(savedRatio+" "))field.spinner.setSelection(i);
        if(VideoWorkflow.matches(workflow)&&!getPreferences(0).contains(mainDraftKey()))dockPrompt.setText("");
        bindingFields = false;
        if(mainReferences!=null)mainReferences.bind();
        updateComposerChip();if(quickOptions!=null&&quickOptions.getVisibility()==View.VISIBLE)rebuildQuickOptions();refreshAdvancedSettings();
    }

    private void saveDraft() {
        if(selectedTab==4)return;
        if (!bindingFields && dockPrompt != null && hasIdeaField())
            getPreferences(0).edit().putString(mainDraftKey(), dockPrompt.getText().toString()).apply();
    }

    private void addField(LinearLayout parent, Field field, String title) {
        if ("Ideia da imagem".equals(field.primaryLabel)) {
            field.edit = dockPrompt;
            dockPrompt.setEnabled(true);
            dockPrompt.setHint(UiLanguage.t("Descreva o que você quer criar…"));
            dockPrompt.setText(String.valueOf(field.original));
            return;
        }
        TextView label = text(title, 12, MUTED, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(12);
        parent.addView(label, labelParams);
        gap(parent, 6);
        if (field.original instanceof Boolean) {
            field.toggle = new android.widget.Switch(this);
            field.toggle.setChecked((Boolean) field.original);
            parent.addView(field.toggle);
        } else if ("Proporção".equals(field.primaryLabel)) {
            field.spinner = new Spinner(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, RATIOS);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            field.spinner.setAdapter(adapter);
            field.spinner.setBackground(round(FIELD, 12, BORDER));
            field.spinner.setPadding(dp(13), dp(9), dp(13), dp(9));
            for (int i = 0; i < RATIOS.length; i++) {
                if (RATIOS[i].equals(String.valueOf(field.original))) field.spinner.setSelection(i);
            }
            parent.addView(field.spinner, new LinearLayout.LayoutParams(-1, dp(50)));
        } else {
            field.edit = new EditText(this);
            styleInput(field.edit);
            field.edit.setText(String.valueOf(field.original));
            if (field.original instanceof Number) field.edit.setInputType(InputType.TYPE_CLASS_NUMBER |
                    InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            else field.edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            parent.addView(field.edit, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private JSONObject builtInReferenceWorkflow(boolean video) throws Exception {
        try(InputStream input=getAssets().open(video?"workflows/minimax_h3_i2v_api.json":"workflows/"+ImageEditWorkflow.asset(!hideAllNsfw))) {
            return new JSONObject(new String(read(input,1_000_000),StandardCharsets.UTF_8));
        }
    }

    private ReferenceTray activeReferences() {
        return versionViewer!=null&&versionViewer.dialog.isShowing()?versionViewer.references:mainReferences;
    }

    private final class ReferenceTray extends LinearLayout {
        final VersionViewer owner;
        final List<String> ids=new ArrayList<>();
        String boundKey="";
        ReferenceTray(VersionViewer owner){super(MainActivity.this);this.owner=owner;setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(dp(10),0,dp(10),dp(6));setVisibility(GONE);}
        boolean videoMode(){return owner==null?VideoWorkflow.matches(workflow):owner.animationEditor;}
        boolean clipMode(){return owner!=null&&owner.editingVideo();}
        boolean framesMode(){return !clipMode()&&videoMode()&&(owner==null?videoFirstLast:owner.firstLastFrame);}
        String key(){if(owner!=null&&owner.extending)return "refs_video_extend_"+owner.currentId;if(clipMode())return "refs_video_edit_"+owner.currentId;String key=owner==null?"refs_workflow_"+(selectedTab==2?"prompter_":selectedTab==3?"project_"+activeProjectId+"_":"")+workflowName:(owner.animationEditor?"refs_video_":"refs_edit_")+owner.currentId;return key+(videoMode()?(framesMode()?"_frames":"_single"):"");}
        int capacity(){if(owner!=null&&owner.extending)return 1;if(clipMode())return 3;return videoMode()?(framesMode()?2:1):owner==null?ReferenceSupport.capacity(workflow):3;}
        String base(){return clipMode()?owner.currentId:owner==null||videoMode()?"":owner.currentId;}
        List<String> selectedIds(){List<String> selected=new ArrayList<>();for(String id:ids)if(!id.isEmpty())selected.add(id);return selected;}
        boolean usable(Generation item){return item!=null&&visibleGeneration(item)&&"done".equals(item.state)&&("image".equals(item.mediaKind)?imageFile(item.id).exists():clipMode()&&item.id.equals(owner.currentId)&&videoFile(item.id).exists());}
        void bind(){
            boundKey=key();ids.clear();boolean saved=getPreferences(0).contains(boundKey);
            try{JSONArray items=new JSONArray(getPreferences(0).getString(boundKey,"[]"));for(int i=0;i<items.length();i++)ids.add(items.optString(i,""));}catch(Exception ignored){}
            if(framesMode()){while(ids.size()<2)ids.add("");if(!saved&&owner!=null&&usable(findGeneration(owner.currentId)))ids.set(0,owner.currentId);}
            else if(owner!=null&&usable(findGeneration(owner.currentId))&&(!videoMode()||clipMode()||!saved)){ids.remove(owner.currentId);ids.add(0,owner.currentId);}
            refresh();
        }
        boolean exposedPrivate(){if(privateCoversEnabled)return false;for(String id:ids){Generation item=findGeneration(id);if(item!=null&&item.nsfw)return true;}return false;}
        void save(){if(!boundKey.isEmpty())getPreferences(0).edit().putString(boundKey,new JSONArray(ids).toString()).apply();}
        void refresh(){
            if(!boundKey.equals(key())){bind();return;}
            if(framesMode()){while(ids.size()<2)ids.add("");for(int i=0;i<2;i++)if(!usable(findGeneration(ids.get(i))))ids.set(i,"");}
            else {ids.removeIf(id->!usable(findGeneration(id)));String fixed=base();if(!fixed.isEmpty()&&usable(findGeneration(fixed))){ids.remove(fixed);ids.add(0,fixed);}}
            while(ids.size()>capacity())ids.remove(ids.size()-1);save();removeAllViews();
            if(framesMode()){
                addFrameBox(0);Button swap=button("",Color.TRANSPARENT,MUTED);FlowDesign.symbol(swap,"swap",UiLanguage.t("Inverter quadros"));addView(swap,new LinearLayout.LayoutParams(dp(40),dp(48)));
                swap.setOnClickListener(v->{String first=ids.get(0);ids.set(0,ids.get(1));ids.set(1,first);save();refresh();});addFrameBox(1);
            }else for(int i=0;i<ids.size();i++) {
                final String id=ids.get(i);Generation item=findGeneration(id);android.widget.FrameLayout tile=new android.widget.FrameLayout(MainActivity.this);
                ImageView image=referenceImage(item);image.setContentDescription(videoMode()?UiLanguage.t("Imagem inicial"):UiLanguage.t("Referência ")+(i+1));
                tile.addView(image,new android.widget.FrameLayout.LayoutParams(dp(58),dp(58),Gravity.CENTER));
                if(!id.equals(base())){Button remove=button("",0xDD202123,TEXT);FlowDesign.symbol(remove,"close",UiLanguage.t("Remover referência ")+(i+1));tile.addView(remove,new android.widget.FrameLayout.LayoutParams(dp(32),dp(32),Gravity.TOP|Gravity.RIGHT));remove.setOnClickListener(v->{ids.remove(id);save();refresh();});}
                else {image.setContentDescription(clipMode()?UiLanguage.t("Clipe base da edição"):UiLanguage.t("Imagem base"));if(clipMode()){TextView badge=text("▶",12,TEXT,false);badge.setGravity(Gravity.CENTER);badge.setBackground(round(0xAA202123,8,0));tile.addView(badge,new android.widget.FrameLayout.LayoutParams(dp(22),dp(22),Gravity.BOTTOM|Gravity.RIGHT));}}
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(66),dp(66));lp.rightMargin=dp(6);addView(tile,lp);
            }
            setVisibility(framesMode()||!ids.isEmpty()?VISIBLE:GONE);
            if(owner!=null&&owner.dialog.getWindow()!=null&&exposedPrivate())owner.dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            if(owner==null)updateComposerChip();else owner.updateModeChip();
        }
        ImageView referenceImage(Generation item){ImageView image=new ImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackground(round(FIELD,12,0));image.setClipToOutline(true);if(item!=null)image.setImageBitmap(covered(item)?coverBitmap(item):galleryThumbnail(thumbFile(item.id)));return image;}
        void addFrameBox(int slot){
            String title=slot==0?"Quadro inicial":"Quadro final";Generation item=findGeneration(ids.get(slot));
            LinearLayout box=column();box.setContentDescription(title+(item==null?UiLanguage.t(", adicionar imagem"):UiLanguage.t(", trocar imagem")));
            android.widget.FrameLayout preview=new android.widget.FrameLayout(MainActivity.this);preview.setBackground(round(FIELD,14,0x44FFFFFF));preview.setClipToOutline(true);
            if(item!=null){preview.addView(referenceImage(item),new android.widget.FrameLayout.LayoutParams(-1,-1));Button remove=button("",0xDD202123,TEXT);FlowDesign.symbol(remove,"close",UiLanguage.t("Remover ")+title.toLowerCase(Locale.ROOT));preview.addView(remove,new android.widget.FrameLayout.LayoutParams(dp(32),dp(32),Gravity.TOP|Gravity.RIGHT));remove.setOnClickListener(v->setFrame(slot,""));}
            else{Button plus=button("",Color.TRANSPARENT,MUTED);FlowDesign.symbol(plus,"plus",UiLanguage.t("Adicionar ")+title.toLowerCase(Locale.ROOT));preview.addView(plus,new android.widget.FrameLayout.LayoutParams(dp(40),dp(40),Gravity.CENTER));plus.setOnClickListener(v->frameMenu(box,slot));}
            box.addView(preview,new LinearLayout.LayoutParams(-1,dp(68)));TextView label=text(title,12,MUTED,false);label.setGravity(Gravity.CENTER);box.addView(label,new LinearLayout.LayoutParams(-1,dp(24)));
            preview.setOnClickListener(v->frameMenu(box,slot));box.setOnClickListener(v->frameMenu(box,slot));addView(box,new LinearLayout.LayoutParams(0,-2,1));
        }
        void frameMenu(View anchor,int slot){PopupMenu menu=new OrganicPopupMenu(MainActivity.this,anchor);menu.getMenu().add(UiLanguage.t("Imagens")).setOnMenuItemClickListener(m->{pickReference(false,slot);return true;});menu.getMenu().add(UiLanguage.t("Biblioteca")).setOnMenuItemClickListener(m->{openReferenceLibrary(slot);return true;});showIconMenu(menu);}
        void setFrame(int slot,String id){if(!framesMode())return;while(ids.size()<2)ids.add("");ids.set(slot,id.isEmpty()||usable(findGeneration(id))?id:"");save();refresh();if(owner==null&&!id.isEmpty()&&mainFold!=null)mainFold.setCollapsed(false);}
        List<Generation> snapshot() throws Exception {
            List<Generation> out=new ArrayList<>();for(String id:ids){Generation item=findGeneration(id);if(!usable(item))throw new IllegalArgumentException(framesMode()?UiLanguage.t("Adicione o quadro inicial e o quadro final."):UiLanguage.t("Referência indisponível. Selecione novamente."));out.add(item);}
            if(clipMode()&&(out.isEmpty()||!out.get(0).id.equals(base())))throw new IllegalArgumentException(UiLanguage.t("Clipe de origem indisponível."));
            if(framesMode()&&out.size()!=2)throw new IllegalArgumentException(UiLanguage.t("Adicione o quadro inicial e o quadro final."));
            if(out.size()>capacity())throw new IllegalArgumentException(UiLanguage.t("Referências excedem o limite do workflow."));return out;
        }
        void replace(List<String> chosen){ids.clear();if(framesMode()){for(int i=0;i<2;i++)ids.add(i<chosen.size()&&usable(findGeneration(chosen.get(i)))?chosen.get(i):"");}
            else{if(!base().isEmpty()&&usable(findGeneration(base())))ids.add(base());for(String id:chosen)if(!ids.contains(id)&&usable(findGeneration(id))&&ids.size()<capacity())ids.add(id);}save();refresh();}
    }

    private void chooseFrameTarget(boolean photos){new FlowDialogs.Builder(this).setTitle(UiLanguage.t("Adicionar quadro")).setItems(new String[]{UiLanguage.t("Quadro inicial"),UiLanguage.t("Quadro final")},(d,slot)->{if(photos)pickReference(false,slot);else openReferenceLibrary(slot);}).show();}


    private void showAddMenu(Button anchor) {
        PopupMenu menu=new OrganicPopupMenu(this,anchor);
        menu.getMenu().add(UiLanguage.t("Imagens")).setOnMenuItemClickListener(m->{pickReference(false);return true;});
        menu.getMenu().add(UiLanguage.t("Biblioteca")).setOnMenuItemClickListener(m->{openReferenceLibrary();return true;});
        menu.getMenu().add(UiLanguage.t("Fragmentos")).setOnMenuItemClickListener(m->{openReferences(0);return true;});
        showIconMenu(menu);
    }

    private void attachSelected(ReferenceTray tray,List<String> chosen) {
        if(tray==null)return;
        if(tray.owner==null&&!chosen.isEmpty()&&mainFold!=null)mainFold.setCollapsed(false);
        if(tray.capacity()==0&&tray.owner==null&&!chosen.isEmpty()) {
            final String prompt=dockPrompt.getText().toString();
            new FlowDialogs.Builder(this).setTitle(UiLanguage.t("Usar imagens como referência"))
                .setMessage(UiLanguage.t("O workflow atual não aceita imagens. Use Klein para criar com até três referências."))
                .setNegativeButton(UiLanguage.t("Manter workflow"),null).setPositiveButton(UiLanguage.t("Usar Klein"),(d,w)->{
                    try{activateWorkflow(builtInReferenceWorkflow(false),!hideAllNsfw?ImageEditWorkflow.SNOFS:ImageEditWorkflow.KLEIN);dockPrompt.setText(prompt);saveDraft();mainReferences.replace(chosen);if(settingsDialog!=null)settingsDialog.dismiss();}
                    catch(Exception e){Toast.makeText(this,UiLanguage.t("Não foi possível abrir Klein."),Toast.LENGTH_LONG).show();}
                }).show();return;
        }
        tray.replace(chosen);
    }

    private void openReferenceLibrary() {openReferenceLibrary(-1);}
    private void openReferenceLibrary(int slot) {
        final ReferenceTray tray=activeReferences();if(tray==null)return;if(tray.framesMode()&&slot<0){chooseFrameTarget(false);return;}tray.refresh();hideKeyboard(activePrompt());hideGalleryReveal();
        final int limit=slot>=0?1:Math.max(1,tray.capacity()==0?3:tray.capacity());
        final List<String> selected=new ArrayList<>();if(slot>=0){if(!tray.ids.get(slot).isEmpty())selected.add(tray.ids.get(slot));}else selected.addAll(tray.selectedIds());
        LinearLayout sheet=column();sheet.setPadding(dp(12),dp(12),dp(12),dp(12));sheet.setBackground(FlowDesign.shape(this,FlowDesign.SURFACE,24,true));
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);TextView title=text(slot<0?UiLanguage.t("Biblioteca"):slot==0?UiLanguage.t("Quadro inicial"):UiLanguage.t("Quadro final"),20,TEXT,true);header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        Button done=button(UiLanguage.t("Adicionar"),ACCENT,BG);header.addView(done,new LinearLayout.LayoutParams(dp(112),dp(48)));sheet.addView(header);
        TextView count=text("",12,MUTED,false);count.setPadding(dp(8),dp(4),0,dp(12));sheet.addView(count);
        final Runnable update=()->{count.setText(selected.size()+" / "+limit+(slot>=0?UiLanguage.t(" · Escolha uma imagem"):tray.clipMode()?UiLanguage.t(" referências"):UiLanguage.t(" imagens")));done.setEnabled(!selected.isEmpty());};update.run();
        android.widget.GridView grid=new android.widget.GridView(this);grid.setNumColumns(3);grid.setHorizontalSpacing(dp(6));grid.setVerticalSpacing(dp(6));grid.setStretchMode(android.widget.GridView.STRETCH_COLUMN_WIDTH);
        final List<Generation> images=new ArrayList<>();for(Generation item:gallerySets)if(tray.usable(item))images.add(item);
        android.widget.BaseAdapter adapter=new android.widget.BaseAdapter(){public int getCount(){return images.size();}public Object getItem(int p){return images.get(p);}public long getItemId(int p){return p;}
            public View getView(int p,View old,android.view.ViewGroup parent){Generation item=images.get(p);int size=Math.max(dp(80),(getResources().getDisplayMetrics().widthPixels-dp(40))/3);
                android.widget.FrameLayout tile=new android.widget.FrameLayout(MainActivity.this);tile.setLayoutParams(new android.widget.AbsListView.LayoutParams(-1,size));
                ImageView image=new ImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(covered(item)?coverBitmap(item):galleryThumbnail(thumbFile(item.id)));image.setBackground(round(FIELD,12,0));image.setClipToOutline(true);tile.addView(image,new android.widget.FrameLayout.LayoutParams(-1,-1));
                boolean chosen=selected.contains(item.id);TextView badge=text(chosen?String.valueOf(selected.indexOf(item.id)+1):"",12,chosen?BG:TEXT,true);badge.setGravity(Gravity.CENTER);badge.setBackground(round(chosen?ACCENT:0x90202123,12,0x99FFFFFF));
                android.widget.FrameLayout.LayoutParams bp=new android.widget.FrameLayout.LayoutParams(dp(24),dp(24),Gravity.RIGHT|Gravity.TOP);bp.setMargins(0,dp(8),dp(8),0);tile.addView(badge,bp);
                tile.setContentDescription(UiLanguage.t("Selecionar imagem ")+(p+1)+(chosen?UiLanguage.t(", selecionada"):""));tile.setOnClickListener(v->{
                    if(item.id.equals(tray.base())){Toast.makeText(MainActivity.this,tray.clipMode()?UiLanguage.t("Este é o clipe base da edição."):UiLanguage.t("Esta é a imagem base da edição."),Toast.LENGTH_SHORT).show();return;}
                    if(selected.contains(item.id))selected.remove(item.id);else if(slot>=0){selected.clear();selected.add(item.id);}else if(selected.size()<limit)selected.add(item.id);else{Toast.makeText(MainActivity.this,UiLanguage.t("Limite de ")+limit+UiLanguage.t(" imagens neste workflow."),Toast.LENGTH_SHORT).show();return;}
                    update.run();notifyDataSetChanged();});return tile;}
        };grid.setAdapter(adapter);sheet.addView(grid,new LinearLayout.LayoutParams(-1,0,1));
        if(images.isEmpty()){TextView empty=text(UiLanguage.t("Nenhuma imagem disponível. Importe pelo botão +."),14,MUTED,false);sheet.addView(empty);}
        Dialog picker=styleSheet(sheet);if(!hideAllNsfw&&!privateCoversEnabled)picker.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        done.setOnClickListener(v->{picker.dismiss();if(slot>=0)tray.setFrame(slot,selected.get(0));else attachSelected(tray,selected);});
    }

    private void openAnimationReferences(List<String> ids,String prompt) {
        if(ids.isEmpty())return;Generation source=findGeneration(ids.get(0));if(source==null)return;
        openViewer(source);if(versionViewer==null||!source.id.equals(versionViewer.currentId))return;
        versionViewer.openEditor(true);versionViewer.references.replace(ids);versionViewer.instruction.setText(prompt);versionViewer.instruction.setSelection(prompt.length());versionViewer.saveEditDraft();
        if(ids.size()>2)Toast.makeText(this,UiLanguage.t("MiniMax usa duas referências: quadro inicial e final."),Toast.LENGTH_LONG).show();
    }

    private void pickReference(boolean animate) {ReferenceTray tray=activeReferences();if(tray!=null&&tray.framesMode()){chooseFrameTarget(true);return;}pickReference(animate,-1);}
    private void pickReference(boolean animate,int slot) {
        ReferenceTray tray=activeReferences();if(tray==null)return;
        getPreferences(0).edit().putString("pending_reference_context",tray.key()).putBoolean("pending_reference_private",!hideAllNsfw)
            .putInt("pending_reference_slot",slot).putString("pending_reference_prompt",activePrompt().getText().toString()).putBoolean("pending_reference_animate",animate&&tray.owner==null).putInt("pending_reference_capacity",tray.capacity()).apply();
        if(!PhotoPicker.launch(this,slot<0?3:1,PICK_ANIMATION_IMAGE))Toast.makeText(this,UiLanguage.t("Nenhuma galeria de fotos disponível neste aparelho."),Toast.LENGTH_LONG).show();
    }

    private void importReferenceImages(Intent data) {
        List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++){Uri uri=data.getClipData().getItemAt(i).getUri();if(uri!=null&&!uris.contains(uri))uris.add(uri);}}
        else if(data.getData()!=null)uris.add(data.getData());
        if(uris.isEmpty())return;
        if(uris.size()>3){Toast.makeText(this,UiLanguage.t("Selecione até três imagens por importação."),Toast.LENGTH_LONG).show();return;}
        final String context=getPreferences(0).getString("pending_reference_context","");final String inputPrompt=getPreferences(0).getString("pending_reference_prompt","");final boolean makePrivate=getPreferences(0).getBoolean("pending_reference_private",true),animate=getPreferences(0).getBoolean("pending_reference_animate",false);
        final int slot=getPreferences(0).getInt("pending_reference_slot",-1);
        getPreferences(0).edit().remove("pending_reference_context").apply();
        result.setText(UiLanguage.t("Importando imagens…"));
        worker.execute(()->{
            List<Generation> imported=new ArrayList<>();int failed=0;
            for(Uri uri:uris){Generation item=new Generation();item.id=UUID.randomUUID().toString();try(InputStream input=getContentResolver().openInputStream(uri)){
                saveImage(item.id,read(input,30_000_000));item.workflowName="Referências";item.prompt="";item.state="done";item.createdAt=System.currentTimeMillis();item.nsfw=makePrivate;imported.add(item);
            }catch(Exception e){failed++;VaultFiles.delete(imageFile(item.id));VaultFiles.delete(thumbFile(item.id));}}
            final int failures=failed;
            runOnUiThread(()->{
                if(imported.isEmpty()){result.setText(UiLanguage.t("Não foi possível importar. Use imagens de até 30 MB."));return;}
                generations.addAll(0,imported);if(!saveFeed()){generations.removeAll(imported);for(Generation item:imported){VaultFiles.delete(imageFile(item.id));VaultFiles.delete(thumbFile(item.id));}result.setText(UiLanguage.t("Falha ao salvar imagens."));return;}
                renderFeed();List<String> ids=new ArrayList<>();for(Generation item:imported)ids.add(item.id);
                ReferenceTray tray=activeReferences();
                if(animate){openAnimationReferences(ids,inputPrompt);}
                else if(tray!=null&&context.equals(tray.key())&&slot>=0){tray.setFrame(slot,ids.get(0));}
                else if(tray!=null&&context.equals(tray.key())){List<String> selected=new ArrayList<>();if(!tray.base().isEmpty())selected.add(tray.base());selected.addAll(ids);for(String id:tray.selectedIds())if(!selected.contains(id))selected.add(id);attachSelected(tray,selected);
                    if(tray.capacity()>0&&selected.size()>tray.capacity())Toast.makeText(this,UiLanguage.t("Todas salvas. Este workflow usa até ")+tray.capacity()+UiLanguage.t(" referências por vez."),Toast.LENGTH_LONG).show();}
                else if(!context.isEmpty()){
                    try{JSONArray draft=new JSONArray(getPreferences(0).getString(context,"[]"));int limit=getPreferences(0).getInt("pending_reference_capacity",0);if(slot>=0){while(draft.length()<2)draft.put("");draft.put(slot,ids.get(0));}else for(String id:ids)if(draft.length()<limit)draft.put(id);getPreferences(0).edit().putString(context,draft.toString()).apply();}catch(Exception ignored){}
                }
                result.setText(imported.size()+UiLanguage.t(" imagens importadas")+(failures>0?" · "+failures+UiLanguage.t(" falharam"):""));
            });
        });
    }

    private void openReferences(int selectedTab) {
        hideGalleryReveal();for(Runnable hide:hideReveals)hide.run();hideKeyboard(activePrompt());referenceStudio.manage(selectedTab);
    }

    private String currentRatio() {
        if(VideoWorkflow.matches(workflow))return effectiveVideoRatio(null);
        if(ReferenceSupport.klein(workflow))return getPreferences(0).getString("ratio_"+workflowName,"Original");
        for(Field field:fields)if("Proporção".equals(field.primaryLabel)&&field.spinner!=null)
            return field.spinner.getSelectedItem().toString().split(" ")[0];
        return getPreferences(0).getString("ratio_"+workflowName,GenerationOptions.supportsImageResolution(workflow)?"1:1":"Original");
    }
    private int currentImageMegapixels(){
        int fallback=1;for(Field field:fields)if("Megapixels".equals(field.primaryLabel)&&field.edit!=null)try{fallback=(int)Math.round(Double.parseDouble(field.edit.getText().toString().trim()));}catch(Exception ignored){}
        return GenerationOptions.imageMegapixels(getPreferences(0).getInt("image_mp_"+workflowName,fallback));
    }
    private void updateComposerChip() {
        updateAgentChip(dockAgentChip,null);
        if(workflowChip!=null)((FlowDesign.Chip)workflowChip).update(currentRatio(),batchCount,VideoWorkflow.matches(workflow));
        if(runButton!=null)runButton.setContentDescription(VideoWorkflow.matches(workflow)?UiLanguage.t("Gerar vídeo"):UiLanguage.t("Gerar imagem"));
        if(dockPrompt!=null&&VideoWorkflow.matches(workflow))dockPrompt.setHint(UiLanguage.t("Descreva seu vídeo…"));
    }
    private void rebuildQuickOptions() {
        if(quickOptions==null)return;
        quickOptions.removeAllViews();fillOptions(quickOptions,null);updateComposerChip();
    }
    private void hideKeyboard(View view) {
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(),0);
        view.clearFocus();
    }

    private void collapseEmpty(TextView view) {
        view.setVisibility(view.length()==0?View.GONE:View.VISIBLE);
        view.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){view.setVisibility(s.length()==0?View.GONE:View.VISIBLE);}
            public void afterTextChanged(android.text.Editable value){}
        });
    }

    private void showIconMenu(PopupMenu menu) {
        MenuIcons.show(this,menu);
    }

    private LinearLayout optionRow(LinearLayout panel) {
        if(panel.getChildCount()>0)gap(panel,4);
        LinearLayout row=row();row.setPadding(dp(2),dp(2),dp(2),dp(2));row.setBackground(round(0xFF202123,12,0));panel.addView(row);return row;
    }

    private Button segment(LinearLayout row,String label,String icon,boolean selected,String description) {
        Button option=button(UiLanguage.t(label),selected?0xFF303134:Color.TRANSPARENT,selected?TEXT:MUTED);
        option.setSelected(selected);option.setSingleLine(true);option.setTextSize(14);option.setTypeface(Branding.body(MainActivity.this,500));option.setPadding(dp(8),0,dp(8),0);
        option.setContentDescription(description+(selected?UiLanguage.t(", selecionado"):""));
        if(icon!=null)FlowDesign.labelIcon(option,icon);
        row.addView(option,new LinearLayout.LayoutParams(0,dp(48),1));return option;
    }

    private JSONObject videoGraph(boolean turbo) throws Exception {
        try(InputStream in=getAssets().open(turbo?"workflows/minimax_h3_turbo_api.json":"workflows/minimax_h3_i2v_api.json")){return LoraSupport.videoLoader(new JSONObject(new String(read(in,1_000_000),StandardCharsets.UTF_8)));}
    }
    private ReferenceTray videoTray(VersionViewer viewer){return viewer==null?mainReferences:viewer.references;}
    private String effectiveVideoRatio(VersionViewer viewer){String ratio=viewer==null?videoRatio:viewer.editingVideo()?viewer.clipRatio:viewer.videoRatio;ReferenceTray tray=videoTray(viewer);return "Original".equals(ratio)&&(tray==null||tray.ids.isEmpty()||tray.ids.get(0).isEmpty())?"16:9":ratio;}
    private void rebuildOptionsFor(VersionViewer viewer){if(viewer==null)rebuildQuickOptions();else viewer.rebuildOptions();}
    private void setFirstLast(VersionViewer viewer,boolean enabled){
        ReferenceTray tray=videoTray(viewer);List<String> previous=new ArrayList<>(tray.ids);
        if(viewer==null)videoFirstLast=enabled;else viewer.firstLastFrame=enabled;
        getPreferences(0).edit().putBoolean(viewer==null?"main_video_first_last":"animation_first_last",enabled).apply();
        boolean existing=getPreferences(0).contains(tray.key());tray.bind();if(!existing&&!previous.isEmpty())tray.replace(previous);rebuildOptionsFor(viewer);
    }
    private void switchComposerMode(boolean video){
        if(video==VideoWorkflow.matches(workflow))return;String prompt=dockPrompt.getText().toString();List<String> refs=mainReferences.selectedIds();
        try{
            if(video){boolean turbo=getPreferences(0).getBoolean("last_video_turbo",true);activateWorkflow(videoGraph(turbo),turbo?VideoWorkflow.TURBO:VideoWorkflow.STANDARD);if(!refs.isEmpty())mainReferences.replace(refs);}
            else{String name=getPreferences(0).getString("last_image_workflow","Kromoss · 8 passos");File file=new File(new File(getFilesDir(),"workflows"),UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8))+".json");
                if(file.exists()){try(InputStream in=VaultFiles.open(file)){JSONObject entry=new JSONObject(new String(read(in,4_000_000),StandardCharsets.UTF_8));activateWorkflow(entry.getJSONObject("prompt"),entry.getString("name"));}}
                else{try(InputStream in=getAssets().open("workflows/kromoss_t2i_api.json")){activateWorkflow(new JSONObject(new String(read(in,4_000_000),StandardCharsets.UTF_8)),"Kromoss · 8 passos");}}}
            if(!prompt.trim().isEmpty()){dockPrompt.setText(prompt);saveDraft();}if(settingsDialog!=null)settingsDialog.dismiss();quickOptions.setVisibility(View.VISIBLE);rebuildQuickOptions();
        }catch(Exception e){result.setText(UiLanguage.t("Falha ao trocar modo: ")+e.getMessage());}
    }
    private void chooseImageEditModel(VersionViewer viewer){
        try{List<String> names=new ArrayList<>();List<JSONObject> graphs=new ArrayList<>();
            names.add(ImageEditWorkflow.KLEIN);graphs.add(bundledWorkflow(ImageEditWorkflow.asset(false)));
            if(!hideAllNsfw){names.add(ImageEditWorkflow.SNOFS);graphs.add(bundledWorkflow(ImageEditWorkflow.asset(true)));}
            WorkflowPicker.show(this,names,graphs,viewerAdvancedName(viewer),false,memoryProfile(),index->{
                getPreferences(0).edit().remove(reuseKey(viewer)).putBoolean("snofs_"+viewer.draftKey(),index==1).apply();viewer.rebuildOptions();},null);
        }catch(Exception e){Toast.makeText(this,UiLanguage.t("Falha ao abrir modelos de edição: ")+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void chooseVideoModel(VersionViewer viewer,View anchor){
        if(viewer==null){chooseWorkflow();return;}
        try{List<String> names=new ArrayList<>();List<JSONObject> graphs=new ArrayList<>();for(boolean turbo:new boolean[]{false,true}){
            names.add(viewer.extending?VideoExtendWorkflow.name(turbo):viewer.editingVideo()?VideoEditWorkflow.name(turbo):turbo?VideoWorkflow.TURBO:VideoWorkflow.STANDARD);graphs.add(videoGraph(turbo));}
            WorkflowPicker.show(this,names,graphs,viewerAdvancedName(viewer),true,memoryProfile(),index->{boolean turbo=index==1;if(viewer.editingVideo())viewer.editTurbo=turbo;else viewer.turbo=turbo;
                getPreferences(0).edit().putBoolean(viewer.editingVideo()?"video_edit_turbo":"animation_turbo",turbo).apply();viewer.rebuildOptions();},null);
        }catch(Exception e){Toast.makeText(this,UiLanguage.t("Falha ao abrir workflows de vídeo."),Toast.LENGTH_SHORT).show();}
    }
    private void fillOptions(LinearLayout panel, VersionViewer viewer) {
        panel.setPadding(dp(8),dp(8),dp(8),dp(8));panel.setBackground(FlowDesign.shape(this,FlowDesign.SURFACE,24,true));
        boolean video=viewer==null?VideoWorkflow.matches(workflow):viewer.animationEditor, clip=viewer!=null&&viewer.editingVideo();
        String unsupported=MemoryProfiles.unavailable(agentTemplate(viewer),memoryProfile());if(viewer!=null&&viewer.extending)unsupported=MemoryProfiles.extensionUnavailable(memoryProfile());if(!unsupported.isEmpty()){TextView reason=text(unsupported,12,MUTED,false);reason.setPadding(dp(12),dp(8),dp(12),dp(8));panel.addView(reason);}
        if(!clip){LinearLayout modes=optionRow(panel);
        segment(modes,UiLanguage.t("Imagem"),"image",!video,UiLanguage.t("Modo imagem")).setOnClickListener(v->{if(viewer==null)switchComposerMode(false);else viewer.openEditor(false);});
        segment(modes,UiLanguage.t("Vídeo"),"video",video,UiLanguage.t("Modo vídeo")).setOnClickListener(v->{if(viewer==null){hideKeyboard(dockPrompt);switchComposerMode(true);}else viewer.openEditor(true);});}
        if(clip){LinearLayout operations=optionRow(panel);
            segment(operations,UiLanguage.t("Editar"),"edit",!viewer.extending,UiLanguage.t("Editar conteúdo do vídeo")).setOnClickListener(v->{if(viewer.extending){viewer.saveEditDraft();viewer.extending=false;viewer.bindDraft();viewer.refresh();viewer.rebuildOptions();}});
            segment(operations,UiLanguage.t("Estender"),"extend",viewer.extending,UiLanguage.t("Cortar trecho e estender vídeo")).setOnClickListener(v->openVideoExtension(viewer));}
        if(video&&!clip){LinearLayout frames=optionRow(panel);android.widget.Switch toggle=new android.widget.Switch(this);toggle.setText(UiLanguage.t("First/Last Frame"));toggle.setTextSize(14);toggle.setTextColor(TEXT);toggle.setPadding(dp(14),0,dp(14),0);toggle.setContentDescription(UiLanguage.t("First/Last Frame"));toggle.setChecked(viewer==null?videoFirstLast:viewer.firstLastFrame);frames.addView(toggle,new LinearLayout.LayoutParams(-1,dp(48)));toggle.setOnCheckedChangeListener((v,on)->setFirstLast(viewer,on));}
        LinearLayout ratios=optionRow(panel);String current=viewer==null?currentRatio():viewer.outputRatio();
        String[] choices=viewer!=null&&viewer.extending?new String[]{"Original"}:new String[]{"Original","16:9","4:3","1:1","3:4","9:16"};
        for(String ratio:choices){
            if("Original".equals(ratio)&&((video&&(videoTray(viewer).ids.isEmpty()||videoTray(viewer).ids.get(0).isEmpty()))||(!video&&viewer==null&&!ReferenceSupport.klein(workflow))))continue;
            Button option=button(ratio,ratio.equals(current)?0xFF303134:Color.TRANSPARENT,ratio.equals(current)?TEXT:MUTED);option.setSelected(ratio.equals(current));option.setSingleLine(true);option.setTextSize(11);option.setTypeface(Branding.body(MainActivity.this,500));option.setPadding(dp(2),dp(6),dp(2),dp(6));
            option.setCompoundDrawables(null,FlowDesign.icon(this,"ratio:"+ratio,18),null,null);option.setCompoundDrawablePadding(dp(4));option.setContentDescription(UiLanguage.t("Proporção ")+ratio+(ratio.equals(current)?UiLanguage.t(", selecionada"):""));ratios.addView(option,new LinearLayout.LayoutParams(0,dp(62),1));
            option.setOnClickListener(v->{if(video){if(viewer==null)videoRatio=ratio;else if(clip)viewer.clipRatio=ratio;else viewer.videoRatio=ratio;getPreferences(0).edit().putString(viewer==null?"main_video_ratio":clip?"video_edit_ratio":"animation_ratio",ratio).apply();}
                else if(viewer!=null){viewer.ratio=ratio;getPreferences(0).edit().putString("variation_ratio",ratio).apply();}
                else{boolean supported=GenerationOptions.supportsImageResolution(workflow);for(Field field:fields)if("Proporção".equals(field.primaryLabel)&&field.spinner!=null)for(int i=0;i<RATIOS.length;i++)if(RATIOS[i].startsWith(ratio+" ")){field.spinner.setSelection(i);supported=true;}
                    if(!supported)Toast.makeText(this,UiLanguage.t("Este workflow não oferece essa proporção."),Toast.LENGTH_SHORT).show();else getPreferences(0).edit().putString("ratio_"+workflowName,ratio).apply();}rebuildOptionsFor(viewer);});
        }
        if(video){LinearLayout resolutions=optionRow(panel);for(int quality:new int[]{33,66,98}){String label=String.format(Locale.ROOT,"%.2f MP",GenerationOptions.videoMegapixels(quality));segment(resolutions,label,null,(viewer==null?videoQuality:viewer.videoQuality)==quality,UiLanguage.t("Resolução ")+label).setOnClickListener(v->{if(viewer==null)videoQuality=quality;else viewer.videoQuality=quality;getPreferences(0).edit().putInt(viewer==null?"main_video_quality":"animation_quality",quality).apply();rebuildOptionsFor(viewer);});}}
        else{LinearLayout resolutions=optionRow(panel);for(int mp:new int[]{1,2,4}){
            Button choice=segment(resolutions,mp+UiLanguage.t(" MP"),null,(viewer==null?currentImageMegapixels():viewer.imageMegapixels)==mp,UiLanguage.t("Resolução ")+mp+UiLanguage.t(" MP"));
            choice.setEnabled(viewer!=null||GenerationOptions.supportsImageResolution(workflow));
            choice.setOnClickListener(v->{if(viewer!=null)viewer.imageMegapixels=mp;else for(Field field:fields)if("Megapixels".equals(field.primaryLabel)&&field.edit!=null)field.edit.setText(String.valueOf(mp));
                getPreferences(0).edit().putInt(viewer==null?"image_mp_"+workflowName:"variation_image_mp",mp).apply();rebuildOptionsFor(viewer);});
        }}
        LinearLayout counts=optionRow(panel);int selected=viewer==null?batchCount:viewer.batch;for(int i=1;i<=4;i++){final int count=i;segment(counts,"x"+i,null,i==selected,UiLanguage.t("Lote de ")+i).setOnClickListener(v->{if(viewer==null){batchCount=count;getPreferences(0).edit().putInt("batch_count",count).apply();}else{viewer.batch=count;getPreferences(0).edit().putInt("variation_batch",count).apply();}rebuildOptionsFor(viewer);});}
        if(video){LinearLayout durations=optionRow(panel);if(clip&&!viewer.extending)segment(durations,UiLanguage.t("Original"),null,viewer.editDurationIndex<0,UiLanguage.t("Duração original do clipe")).setOnClickListener(v->{viewer.editDurationIndex=-1;getPreferences(0).edit().putInt("video_edit_duration",-1).apply();rebuildOptionsFor(viewer);});int[] seconds={3,5,8,10};for(int i=0;i<seconds.length;i++){final int index=i;segment(durations,seconds[i]+UiLanguage.t("s"),null,i==(viewer==null?videoDuration:clip?viewer.editDurationIndex:viewer.durationIndex),UiLanguage.t("Duração aproximada ")+seconds[i]+UiLanguage.t(" segundos")).setOnClickListener(v->{if(viewer==null)videoDuration=index;else if(clip)viewer.editDurationIndex=index;else viewer.durationIndex=index;getPreferences(0).edit().putInt(viewer==null?"main_video_duration":clip?"video_edit_duration":"animation_duration",index).apply();rebuildOptionsFor(viewer);});}}
        if(viewer==null&&!video&&BoxLayout.supported(workflow)){
            JSONObject layout=currentBoxLayout();JSONArray boxes=layout.optJSONArray("boxes");int count=boxes==null?0:boxes.length();boolean on=layout.optBoolean("enabled")&&count>0;
            LinearLayout composition=optionRow(panel);composition.setGravity(Gravity.CENTER_VERTICAL);
            Button regions=button(count>0?UiLanguage.t("Boxes · ")+count:UiLanguage.t("Adicionar boxes"),Color.TRANSPARENT,TEXT);FlowDesign.labelIcon(regions,"select");regions.setTextSize(14);regions.setSingleLine(true);regions.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);regions.setContentDescription(UiLanguage.t("Editar composição por boxes"));composition.addView(regions,new LinearLayout.LayoutParams(0,dp(48),1));regions.setOnClickListener(v->openBoxLayout());
            android.widget.Switch toggle=new android.widget.Switch(this);toggle.setContentDescription(UiLanguage.t("Usar boxes"));toggle.setChecked(on);toggle.setPadding(dp(8),0,dp(12),0);composition.addView(toggle,new LinearLayout.LayoutParams(dp(68),dp(48)));
            toggle.setOnCheckedChangeListener((v,checked)->{if(checked&&count==0){toggle.setChecked(false);openBoxLayout();return;}try{layout.put("enabled",checked);getPreferences(0).edit().putString("layout_"+mainDraftKey(),layout.toString()).apply();}catch(Exception ignored){}});
        }
        if(viewer!=null&&viewer.extending){LinearLayout rangeRow=optionRow(panel);Button trim=button(UiLanguage.t("Trecho · ")+VideoExtendWorkflow.summary(viewer.extensionRange),Color.TRANSPARENT,TEXT);trim.setContentDescription(UiLanguage.t("Ajustar corte para extensão"));rangeRow.addView(trim,new LinearLayout.LayoutParams(-1,dp(48)));trim.setOnClickListener(v->openVideoExtension(viewer));}
        LinearLayout modelRow=optionRow(panel);String name=viewer==null?workflowName:viewer.extending?VideoExtendWorkflow.name(viewer.editTurbo):clip?VideoEditWorkflow.name(viewer.editTurbo):video?(viewer.turbo?VideoWorkflow.TURBO:VideoWorkflow.STANDARD):viewerAdvancedName(viewer);
        Button model=button(name,Color.TRANSPARENT,TEXT);model.setSingleLine(true);model.setEllipsize(android.text.TextUtils.TruncateAt.END);model.setContentDescription(UiLanguage.t("Escolher workflow"));model.setCompoundDrawables(FlowDesign.icon(this,video?"video":"image",20),null,FlowDesign.icon(this,"down",18),null);model.setCompoundDrawablePadding(dp(8));modelRow.addView(model,new LinearLayout.LayoutParams(0,dp(48),1));
        model.setOnClickListener(v->{if(video)chooseVideoModel(viewer,model);else if(viewer==null)chooseWorkflow();else chooseImageEditModel(viewer);});
        Button more=button("",Color.TRANSPARENT,TEXT);FlowDesign.symbol(more,"settings",UiLanguage.t("Ajustes avançados de geração"));more.setOnClickListener(v->{if(viewer==null)openSettings();else openAdvancedSettings(viewer);});modelRow.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));
    }


    private void loadProjects(){
        try{JSONArray array=new JSONArray(getPreferences(0).getString("projects_v1","[]"));for(int i=0;i<array.length();i++){ProjectAlbum p=ProjectAlbum.fromJson(array.getJSONObject(i));if(validId(p.id))projects.add(p);}}catch(Exception ignored){}
    }
    private boolean saveProjects(){try{JSONArray array=new JSONArray();for(ProjectAlbum p:projects)array.put(p.toJson());return getPreferences(0).edit().putString("projects_v1",array.toString()).commit();}catch(Exception e){return false;}}
    private ProjectAlbum project(String id){for(ProjectAlbum p:projects)if(p.id.equals(id))return p;return null;}
    private boolean familyInProject(Generation item,String project){String root=familyId(item);for(Generation member:generations)if(familyId(member).equals(root)&&member.projectIds.contains(project)&&!member.temporary)return true;return false;}
    private List<Generation> projectCovers(String project){return GallerySets.covers(generations,new GallerySets.Access<Generation>(){
        public String id(Generation item){return item.id;}public String root(Generation item){return familyId(item);}
        public boolean visible(Generation item){return published(item)&&(project.isEmpty()||familyInProject(item,project));}
        public boolean ready(Generation item){return "done".equals(item.state)&&thumbFile(item.id).isFile();}
        public long time(Generation item){return item.createdAt;}public String preferred(String root){return getPreferences(0).getString("gallery_cover_"+root,"");}
    });}
    private File projectCoverFile(ProjectAlbum project){return project.coverFile.matches("[A-Za-z0-9_-]+\\.jpg")?new File(new File(getFilesDir(),"project-covers"),project.coverFile):null;}
    private Bitmap projectCover(ProjectAlbum project,List<Generation> covers){
        File custom=projectCoverFile(project);if(custom!=null&&custom.isFile()&&(!project.privateCover||!hideAllNsfw)){
            if(project.privateCover&&privateCoversEnabled){Generation fake=new Generation();fake.id=project.id;fake.workflowName=project.name;fake.mediaWidth=4;fake.mediaHeight=3;return coverBitmap(fake);}return galleryThumbnail(custom);
        }
        Generation selected=findGeneration(project.coverId);if(selected==null||!published(selected)||!familyInProject(selected,project.id)||!thumbFile(selected.id).isFile())selected=covers.isEmpty()?null:covers.get(0);
        return selected==null?null:covered(selected)?coverBitmap(selected):galleryThumbnail(thumbFile(selected.id));
    }
    private void renderProjects(){
        projectsPage.removeAllViews();if(!activeProjectId.isEmpty())return;
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);header.addView(text(projects.size()+(projects.size()==1?UiLanguage.t(" projeto"):UiLanguage.t(" projetos")),13,MUTED,false),new LinearLayout.LayoutParams(0,dp(48),1));
        Button add=button("",Color.TRANSPARENT,TEXT);FlowDesign.symbol(add,"plus",UiLanguage.t("Novo projeto"));header.addView(add,new LinearLayout.LayoutParams(dp(48),dp(48)));add.setOnClickListener(v->editProject(null,null));projectsPage.addView(header);
        if(projects.isEmpty()){TextView empty=text(UiLanguage.t("Organize suas gerações\nCrie um projeto e reúna imagens e vídeos."),15,MUTED,false);empty.setPadding(dp(16),dp(32),dp(16),dp(32));projectsPage.addView(empty);return;}
        LinearLayout line=null;for(int index=0;index<projects.size();index++){
            ProjectAlbum p=projects.get(index);if(index%2==0){line=row();projectsPage.addView(line);}
            LinearLayout card=column();card.setBackground(round(CARD,20,0));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.setMargins(dp(4),dp(4),dp(4),dp(4));line.addView(card,cp);
            List<Generation> covers=projectCovers(p.id);ImageView photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);photo.setBackground(round(FIELD,20,0));photo.setClipToOutline(true);Bitmap bitmap=projectCover(p,covers);photo.setImageBitmap(bitmap);if(bitmap==null)photo.setImageDrawable(FlowDesign.icon(this,"folder",40));photo.setContentDescription(UiLanguage.t("Abrir projeto ")+p.name);card.addView(photo,new LinearLayout.LayoutParams(-1,dp(132)));photo.setOnClickListener(v->openProject(p));
            LinearLayout title=row();title.setPadding(dp(10),dp(4),0,dp(6));title.setGravity(Gravity.CENTER_VERTICAL);LinearLayout names=column();TextView name=text(p.name,14,TEXT,true);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);names.addView(name);names.addView(text(covers.size()+UiLanguage.t(" mídias"),11,MUTED,false));title.addView(names,new LinearLayout.LayoutParams(0,-2,1));Button options=button("⋮",Color.TRANSPARENT,TEXT);options.setContentDescription(UiLanguage.t("Opções do projeto ")+p.name);title.addView(options,new LinearLayout.LayoutParams(dp(40),dp(48)));options.setOnClickListener(v->projectMenu(options,p));card.addView(title);names.setOnClickListener(v->openProject(p));
        }
        if(projects.size()%2==1)line.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
    }
    private void openProject(ProjectAlbum p){saveDraft();activeProjectId=p.id;selectTab(3,false);}
    private void editProject(ProjectAlbum existing,Generation attach){
        EditText name=new EditText(this);styleInput(name);name.setSingleLine(true);name.setHint(UiLanguage.t("Nome do projeto"));name.setContentDescription(UiLanguage.t("Nome do projeto"));if(existing!=null)name.setText(existing.name);
        AlertDialog dialog=new FlowDialogs.Builder(this).setTitle(existing==null?UiLanguage.t("Novo projeto"):UiLanguage.t("Renomear projeto")).setView(name).setNegativeButton(UiLanguage.t("Cancelar"),null).setPositiveButton(UiLanguage.t("Salvar"),null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String value=name.getText().toString().trim();if(value.isEmpty()||value.length()>60){name.setError(UiLanguage.t("Use 1 a 60 caracteres."));return;}
            ProjectAlbum p=existing==null?new ProjectAlbum():existing;String previous=p.name;p.name=value;if(existing==null)projects.add(p);
            if(!saveProjects()){p.name=previous;if(existing==null)projects.remove(p);name.setError(UiLanguage.t("Não foi possível salvar."));return;}dialog.dismiss();
            if(attach!=null)addFamilyToProject(p,attach);else openProject(p);renderFeed();}));dialog.show();
    }
    private void chooseProjectFor(Generation item){
        PopupMenu menu=new OrganicPopupMenu(this,generationDock);for(ProjectAlbum p:projects)menu.getMenu().add(p.name).setIcon(FlowDesign.icon(this,"folder",22)).setOnMenuItemClickListener(m->{addFamilyToProject(p,item);return true;});
        menu.getMenu().add(UiLanguage.t("Novo projeto…")).setOnMenuItemClickListener(m->{editProject(null,item);return true;});showIconMenu(menu);
    }
    private void addFamilyToProject(ProjectAlbum p,Generation item){
        if(item.temporary){saveTrial(item);if(item.temporary)return;}
        List<Generation> changed=new ArrayList<>();String root=familyId(item);for(Generation member:generations)if(!member.temporary&&familyId(member).equals(root)&&member.projectIds.add(p.id))changed.add(member);
        if(!saveFeed()){for(Generation member:changed)member.projectIds.remove(p.id);Toast.makeText(this,UiLanguage.t("Falha ao salvar projeto."),Toast.LENGTH_LONG).show();return;}
        renderFeed();Toast.makeText(this,UiLanguage.t("Adicionado a ")+p.name,Toast.LENGTH_SHORT).show();
    }
    private void projectMenu(View anchor,ProjectAlbum p){
        PopupMenu menu=new OrganicPopupMenu(this,anchor);
        menu.getMenu().add(UiLanguage.t("Adicionar gerações")).setOnMenuItemClickListener(m->{projectPicker(p,false);return true;});
        menu.getMenu().add(UiLanguage.t("Renomear")).setOnMenuItemClickListener(m->{editProject(p,null);return true;});
        menu.getMenu().add(UiLanguage.t("Escolher capa")).setOnMenuItemClickListener(m->{projectPicker(p,true);return true;});
        menu.getMenu().add(UiLanguage.t("Enviar capa")).setOnMenuItemClickListener(m->{pendingProjectCover=p.id;getPreferences(0).edit().putString("pending_project_cover",p.id).apply();Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,PICK_PROJECT_COVER);return true;});
        menu.getMenu().add(UiLanguage.t("Apagar projeto")).setOnMenuItemClickListener(m->{new FlowDialogs.Builder(this).setTitle(UiLanguage.t("Apagar ")+p.name+"?").setMessage(UiLanguage.t("Remove apenas a organização do projeto. Gerações continuam no Feed e na Galeria.")).setNegativeButton(UiLanguage.t("Cancelar"),null).setPositiveButton(UiLanguage.t("Apagar"),(d,w)->{
            int at=projects.indexOf(p);projects.remove(p);if(!saveProjects()){projects.add(at,p);return;}saveDraft();for(Generation item:generations)item.projectIds.remove(p.id);saveFeed();File cover=projectCoverFile(p);if(cover!=null)VaultFiles.delete(cover);if(activeProjectId.equals(p.id))activeProjectId="";selectTab(3,false);
        }).show();return true;});showIconMenu(menu);
    }
    private void projectPicker(ProjectAlbum p,boolean cover){
        List<Generation> choices=projectCovers("");choices.removeIf(item->!"done".equals(item.state)||!thumbFile(item.id).isFile());java.util.Set<String> chosen=new java.util.LinkedHashSet<>();
        LinearLayout sheet=column();sheet.setPadding(dp(12),dp(12),dp(12),dp(12));LinearLayout head=row();head.addView(text(cover?UiLanguage.t("Escolher capa"):UiLanguage.t("Adicionar gerações"),18,TEXT,true),new LinearLayout.LayoutParams(0,dp(48),1));Button close=button("",Color.TRANSPARENT,TEXT);FlowDesign.symbol(close,"close",UiLanguage.t("Fechar seleção do projeto"));head.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));sheet.addView(head);
        MasonryGallery picker=new MasonryGallery(this);sheet.addView(picker,new LinearLayout.LayoutParams(-1,0,1));Button done=button(cover?UiLanguage.t("Usar capa"):UiLanguage.t("Adicionar"),ACCENT,BG);done.setEnabled(false);sheet.addView(done,new LinearLayout.LayoutParams(-1,dp(48)));
        MasonryGallery.Adapter adapter=new MasonryGallery.Adapter(){public int getCount(){return choices.size();}public Object getItem(int pos){return choices.get(pos);}public long getItemId(int pos){return pos;}double aspect(int pos){return galleryAspect(choices.get(pos));}String key(int pos){return choices.get(pos).id;}
            public View getView(int pos,View recycled,android.view.ViewGroup parent){Generation item=choices.get(pos);android.widget.FrameLayout tile=new android.widget.FrameLayout(MainActivity.this);tile.setBackground(round(FIELD,14,0));tile.setClipToOutline(true);ImageView image=new ImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(covered(item)?coverBitmap(item):galleryThumbnail(thumbFile(item.id)));tile.addView(image,new android.widget.FrameLayout.LayoutParams(-1,-1));
                TextView check=text(chosen.contains(item.id)?"✓":"○",24,TEXT,true);check.setPadding(dp(8),dp(4),dp(8),0);check.setGravity(Gravity.TOP|Gravity.RIGHT);check.setBackgroundColor(chosen.contains(item.id)?0x55405142:0x11000000);tile.addView(check,new android.widget.FrameLayout.LayoutParams(-1,-1));tile.setContentDescription(UiLanguage.t("Selecionar geração ")+item.id);tile.setOnClickListener(v->{if(cover)chosen.clear();if(!chosen.remove(item.id))chosen.add(item.id);notifyDataSetChanged();done.setEnabled(!chosen.isEmpty());done.setText(cover?UiLanguage.t("Usar capa"):UiLanguage.t("Adicionar ")+chosen.size());});return tile;}
        };picker.setAdapter(adapter);if(choices.isEmpty())sheet.addView(text(UiLanguage.t("Salve uma geração para adicioná-la a um projeto."),13,MUTED,false));
        Dialog dialog=styleSheet(sheet);if(!hideAllNsfw&&!privateCoversEnabled)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);close.setOnClickListener(v->dialog.dismiss());
        done.setOnClickListener(v->{for(String id:chosen){Generation item=findGeneration(id);if(item==null||!published(item))continue;addFamilyToProject(p,item);if(cover){String oldId=p.coverId,oldFile=p.coverFile;p.coverId=item.id;p.coverFile="";if(!saveProjects()){p.coverId=oldId;p.coverFile=oldFile;}else if(oldFile.matches("[A-Za-z0-9_-]+\\.jpg"))VaultFiles.delete(new File(new File(getFilesDir(),"project-covers"),oldFile));}}
            dialog.dismiss();renderFeed();});
    }
    private void importProjectCover(Uri uri){
        String id=pendingProjectCover.isEmpty()?getPreferences(0).getString("pending_project_cover",""):pendingProjectCover;ProjectAlbum p=project(id);if(p==null)return;
        Bitmap bitmap=null;File file=null;try(InputStream input=getContentResolver().openInputStream(uri)){
            byte[] bytes=read(input,15_000_000);BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);options.inJustDecodeBounds=false;options.inSampleSize=Math.max(1,Math.max(options.outWidth,options.outHeight)/1200);bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap==null)throw new IllegalArgumentException(UiLanguage.t("Imagem inválida."));bitmap=orientBitmap(bitmap,imageOrientation(new java.io.ByteArrayInputStream(bytes)));
            File dir=new File(getFilesDir(),"project-covers");dir.mkdirs();file=new File(dir,UUID.randomUUID()+".jpg");try(OutputStream output=VaultFiles.output(file)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,88,output))throw new IllegalStateException(UiLanguage.t("Falha ao salvar capa."));}
            String previous=p.coverFile,oldId=p.coverId;boolean oldPrivate=p.privateCover;p.coverFile=file.getName();p.coverId="";p.privateCover=!hideAllNsfw;
            if(!saveProjects()){p.coverFile=previous;p.coverId=oldId;p.privateCover=oldPrivate;throw new IllegalStateException(UiLanguage.t("Falha ao salvar projeto."));}if(previous.matches("[A-Za-z0-9_-]+\\.jpg"))VaultFiles.delete(new File(dir,previous));file=null;renderFeed();
        }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}finally{if(bitmap!=null)bitmap.recycle();if(file!=null)VaultFiles.delete(file);pendingProjectCover="";getPreferences(0).edit().remove("pending_project_cover").apply();}
    }

    private String advancedKey(JSONObject graph,String name){return "advanced_"+UUID.nameUUIDFromBytes((name+"|"+(graph==null?"":graph.toString())).getBytes(StandardCharsets.UTF_8));}
    private String advancedValue(JSONObject graph,String name){return getPreferences(0).getString(advancedKey(graph,name),"{}");}
    private JSONObject viewerAdvancedGraph(VersionViewer viewer)throws Exception{
        return editTemplate(viewer);
    }
    private String viewerAdvancedName(VersionViewer viewer){return viewer.extending?VideoExtendWorkflow.name(viewer.editTurbo):viewer.editingVideo()?VideoEditWorkflow.name(viewer.editTurbo):viewer.animationEditor?(viewer.turbo?VideoWorkflow.TURBO:VideoWorkflow.STANDARD):imageEditName(viewer);}
    private String imageEditName(VersionViewer viewer){try{return ImageEditWorkflow.name(editTemplate(viewer));}catch(Exception e){return ImageEditWorkflow.KLEIN;}}
    private String viewerAdvancedValue(VersionViewer viewer){try{return advancedValue(viewerAdvancedGraph(viewer),viewerAdvancedName(viewer));}catch(Exception e){return "{}";}}
    private void refreshAdvancedSettings(){if(advancedSettings!=null)buildAdvancedSettings(advancedSettings,workflow,workflowName);}
    private void openAdvancedSettings(VersionViewer viewer){
        try{LinearLayout sheet=column();sheet.setPadding(dp(20),dp(12),dp(20),dp(16));LinearLayout head=row();head.addView(text(UiLanguage.t("Geração avançada"),19,TEXT,true),new LinearLayout.LayoutParams(0,dp(48),1));Button close=button("",Color.TRANSPARENT,TEXT);FlowDesign.symbol(close,"close",UiLanguage.t("Fechar ajustes avançados"));head.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));sheet.addView(head);
            ScrollView scroll=new ScrollView(this);LinearLayout controls=column();scroll.addView(controls);sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));buildAdvancedSettings(controls,viewerAdvancedGraph(viewer),viewerAdvancedName(viewer));Dialog dialog=styleSheet(sheet);close.setOnClickListener(v->dialog.dismiss());
        }catch(Exception e){Toast.makeText(this,UiLanguage.t("Não foi possível abrir os ajustes deste workflow."),Toast.LENGTH_LONG).show();}
    }
    private void buildAdvancedSettings(LinearLayout list,JSONObject graph,String name){
        list.removeAllViews();LinearLayout current=settingsSection(list,"layers",UiLanguage.t("Workflow ativo"),"");TextView model=text(name,14,MUTED,false);model.setMaxLines(2);model.setEllipsize(android.text.TextUtils.TruncateAt.END);current.addView(model);
        if(graph==null){list.addView(text(UiLanguage.t("Escolha um workflow no compositor."),13,MUTED,false));return;}
        final String key=advancedKey(graph,name);final JSONObject saved;try{saved=new JSONObject(advancedValue(graph,name));}catch(Exception e){return;}
        LinearLayout exclusions=settingsSection(list,"minus",UiLanguage.t("Negative prompt"),NegativePrompt.explanation(graph));
        EditText negative=new EditText(this);styleInput(negative);negative.setMinLines(2);negative.setMaxLines(5);negative.setTextSize(14);negative.setGravity(Gravity.TOP|Gravity.START);negative.setHint(UiLanguage.t("O que evitar nesta geração…"));negative.setContentDescription(UiLanguage.t("Negative prompt"));negative.setText(saved.optString(NegativePrompt.KEY,NegativePrompt.value(graph)));exclusions.addView(negative,new LinearLayout.LayoutParams(-1,-2));
        negative.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence v,int a,int c,int f){}public void onTextChanged(CharSequence v,int a,int b,int c){}public void afterTextChanged(android.text.Editable text){try{saved.put(NegativePrompt.KEY,text.toString());getPreferences(0).edit().putString(key,saved.toString()).apply();}catch(Exception ignored){}}});
        List<AdvancedGeneration.Control> controls=AdvancedGeneration.controls(graph);
        if(controls.isEmpty()){list.addView(text(UiLanguage.t("Este workflow não expõe ajustes de amostragem."),13,MUTED,false));return;}
        List<EditText> seeds=new ArrayList<>();boolean hasSeed=false;for(AdvancedGeneration.Control control:controls)hasSeed|=control.seed();
        LinearLayout seedSection=hasSeed?settingsSection(list,"reuse",UiLanguage.t("Seed"),""):null;LinearLayout sampling=settingsSection(list,"settings",UiLanguage.t("Amostragem"),"");
        if(hasSeed){android.widget.Switch random=new android.widget.Switch(this);random.setText(UiLanguage.t("Aleatória a cada geração"));random.setTextColor(TEXT);random.setChecked(saved.optBoolean("randomSeed",true));seedSection.addView(random,new LinearLayout.LayoutParams(-1,dp(48)));
            random.setOnCheckedChangeListener((v,on)->{try{saved.put("randomSeed",on);getPreferences(0).edit().putString(key,saved.toString()).apply();for(EditText seed:seeds){seed.setEnabled(!on);seed.setAlpha(on?.45f:1f);}}catch(Exception ignored){}});}
        java.util.Map<String,Integer> counts=new HashMap<>();for(AdvancedGeneration.Control c:controls)counts.put(c.label,counts.getOrDefault(c.label,0)+1);
        for(AdvancedGeneration.Control control:controls){
            LinearLayout group=control.seed()?seedSection:sampling;LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(0,dp(4),0,dp(4));group.addView(line);TextView label=text(UiLanguage.t(control.label)+(counts.get(control.label)>1?" · #"+control.sourceNode:""),13,MUTED,false);label.setMaxLines(2);line.addView(label,new LinearLayout.LayoutParams(0,-2,1));
            String value=String.valueOf(saved.opt(control.id())==null?control.value:saved.opt(control.id()));
            if(control.choice()){
                Button choose=button(value,FIELD,TEXT);choose.setContentDescription(UiLanguage.t("Escolher ")+control.label);choose.setSingleLine(true);choose.setEllipsize(android.text.TextUtils.TruncateAt.END);FlowDesign.labelIcon(choose,"down");line.addView(choose,new LinearLayout.LayoutParams(dp(168),dp(48)));
                choose.setOnClickListener(v->{if(baseUrl==null){Toast.makeText(this,UiLanguage.t("Conecte ao servidor para buscar opções."),Toast.LENGTH_SHORT).show();return;}
                    choose.setEnabled(false);String address=baseUrl;worker.execute(()->{try{
                        JSONObject node=getJson(address+"/object_info/"+URLEncoder.encode(control.type,"UTF-8")).getJSONObject(control.type).getJSONObject("input");
                        JSONObject definitions=node.optJSONObject("required");JSONArray definition=definitions==null?null:definitions.optJSONArray(control.key);
                        if(definition==null&&node.optJSONObject("optional")!=null)definition=node.getJSONObject("optional").optJSONArray(control.key);
                        JSONArray options=definition==null?null:definition.optJSONArray(0);if(options==null||options.length()==0)throw new IllegalStateException(UiLanguage.t("Opções indisponíveis neste servidor."));
                        List<String> names=new ArrayList<>();for(int i=0;i<options.length();i++)names.add(options.getString(i));
                        runOnUiThread(()->{choose.setEnabled(true);if(!choose.isAttachedToWindow())return;PopupMenu menu=new OrganicPopupMenu(this,choose);for(String option:names)menu.getMenu().add(option).setOnMenuItemClickListener(m->{try{saved.put(control.id(),option);getPreferences(0).edit().putString(key,saved.toString()).apply();choose.setText(option);}catch(Exception ignored){}return true;});menu.show();});
                    }catch(Exception e){runOnUiThread(()->{choose.setEnabled(true);Toast.makeText(this,UiLanguage.t("Não foi possível buscar ")+control.label+UiLanguage.t(". Verifique a conexão."),Toast.LENGTH_LONG).show();});}});
                });
            }else{
                EditText input=new EditText(this);styleInput(input);input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_NUMBER|(control.seed()||control.key.equals("steps")?0:InputType.TYPE_NUMBER_FLAG_DECIMAL));input.setText(value);input.setContentDescription(UiLanguage.t("Ajustar ")+control.label);input.setTextSize(14);input.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);input.setBackground(round(BG,14,0));line.addView(input,new LinearLayout.LayoutParams(dp(control.seed()?168:104),dp(48)));
                if(control.seed()){seeds.add(input);input.setEnabled(!saved.optBoolean("randomSeed",true));input.setAlpha(input.isEnabled()?1f:.45f);}
                input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence v,int a,int c,int f){}public void onTextChanged(CharSequence v,int a,int b,int c){}public void afterTextChanged(android.text.Editable text){try{saved.put(control.id(),AdvancedGeneration.validate(control,text.toString()));getPreferences(0).edit().putString(key,saved.toString()).apply();input.setError(null);}catch(Exception e){input.setError(UiLanguage.t("Valor inválido; último valor válido mantido."));}}});
            }
        }
        if(sampling.getChildCount()==1)list.removeView(sampling);gap(list,12);Button reset=button(UiLanguage.t("Restaurar ajustes"),Color.TRANSPARENT,MUTED);FlowDesign.labelIcon(reset,"refresh");list.addView(reset,new LinearLayout.LayoutParams(-1,dp(48)));reset.setOnClickListener(v->{getPreferences(0).edit().remove(key).apply();buildAdvancedSettings(list,graph,name);});
    }

    private void updateLoraButton() {
        if(loraButton==null&&dockLoraChip==null)return;
        int count=0;
        try {
            if(workflow!=null) {
                JSONObject choices=new JSONObject(getPreferences(0).getString(loraKey(),LoraSupport.defaults(workflow,LoraSupport.loaders(workflow)).toString()));
                Iterator<String> ids=choices.keys();while(ids.hasNext()){JSONArray entries=choices.getJSONArray(ids.next());
                    for(int i=0;i<entries.length();i++)if(entries.getJSONObject(i).optBoolean("on")&&(!hideAllNsfw||!LoraPrivacy.restricted(entries.getJSONObject(i).optString("lora"))))count++;}
            }
        }catch(Exception ignored){}
        if(loraButton!=null)loraButton.setText(count==0?UiLanguage.t("LoRAs · adicionar"):UiLanguage.t("LoRAs · ")+count+UiLanguage.t(" ativos"));
        if(dockLoraChip!=null){dockLoraChip.setText(count==0?UiLanguage.t("LoRAs"):UiLanguage.t("LoRAs ")+count);dockLoraChip.setContentDescription(UiLanguage.t("Configurar LoRAs, ")+count+UiLanguage.t(" ativos"));dockLoraChip.setVisibility(workflow!=null&&!LoraSupport.loaders(workflow).isEmpty()?View.VISIBLE:View.GONE);}
    }

    private String loraKey() {return loraKey(workflow,workflowName);}
    private String loraKey(JSONObject graph,String name) {
        return "loras_"+UUID.nameUUIDFromBytes((baseUrl+"|"+name+"|"+(graph==null?"":graph.toString())).getBytes(StandardCharsets.UTF_8));
    }

    private void applySelectedLoras(JSONObject graph,String saved,String address)throws Exception{
        JSONObject choices=new JSONObject(saved);if(hideAllNsfw)LoraPrivacy.disableChoices(choices);LoraSupport.apply(graph,choices,fetchLoras(address));
    }
    private List<String> fetchLoras(String address) throws Exception {
        LoraPrivacy.refresh(address,url->getJson(url));
        JSONObject info = getJson(address + "/object_info/LoraLoader");
        JSONArray names = info.getJSONObject("LoraLoader").getJSONObject("input").getJSONObject("required").getJSONArray("lora_name").getJSONArray(0);
        List<String> list = new ArrayList<>();
        for (int i=0; i<names.length(); i++) if(!hideAllNsfw||!LoraPrivacy.restricted(names.getString(i)))list.add(names.getString(i));
        Collections.sort(list, String.CASE_INSENSITIVE_ORDER); return list;
    }

    private String loraContextKey(VersionViewer owner){try{return owner==null?loraKey():loraKey(editTemplate(owner),viewerAdvancedName(owner));}catch(Exception e){return "";}}
    private void updateViewerLoras(VersionViewer owner){
        if(owner.loras==null)return;try{JSONObject template=editTemplate(owner);List<String> loaders=LoraSupport.loaders(template);owner.loras.setVisibility(loaders.isEmpty()?View.GONE:View.VISIBLE);JSONObject choices=new JSONObject(getPreferences(0).getString(loraKey(template,viewerAdvancedName(owner)),LoraSupport.defaults(template,loaders).toString()));if(hideAllNsfw)LoraPrivacy.disableChoices(choices);int count=0;for(Iterator<String> ids=choices.keys();ids.hasNext();){JSONArray entries=choices.getJSONArray(ids.next());for(int i=0;i<entries.length();i++)if(entries.getJSONObject(i).optBoolean("on"))count++;}owner.loras.setText(count==0?UiLanguage.t("LoRAs"):UiLanguage.t("LoRAs ")+count);}catch(Exception e){owner.loras.setVisibility(View.GONE);}
    }

    private void openLoras(){openLoras(null);}
    private void openLoras(VersionViewer owner) {
        final JSONObject template;final String name;try{template=owner==null?workflow:editTemplate(owner);name=owner==null?workflowName:viewerAdvancedName(owner);}catch(Exception error){Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();return;}
        if (template == null || baseUrl == null) { Toast.makeText(this,UiLanguage.t("Escolha um workflow e conecte ao servidor."),Toast.LENGTH_LONG).show(); return; }
        final List<String> ids = LoraSupport.loaders(template);
        if (ids.isEmpty()) { new FlowDialogs.Builder(this).setTitle(UiLanguage.t("LoRAs indisponíveis"))
                .setMessage(UiLanguage.t("Nenhum Power Lora Loader ativo com família reconhecida. Moss e MiniMax H3 usam suas pastas; outras famílias precisam de um mapeamento seguro.")).setPositiveButton(UiLanguage.t("OK"),null).show(); return; }
        final String address=baseUrl, key=loraKey(template,name);
        (owner==null?result:owner.status).setText(UiLanguage.t("Buscando LoRAs instalados…"));
        worker.execute(() -> {
            try {
                List<String> installed=fetchLoras(address);
                runOnUiThread(() -> {
                    if(isFinishing()||isDestroyed()||owner!=null&&!owner.dialog.isShowing()||!key.equals(loraContextKey(owner))) return;
                    (owner==null?result:owner.status).setText(""); showLoraEditor(ids,installed,key,template,owner);
                });
            } catch(Exception e) {runOnUiThread(() -> (owner==null?result:owner.status).setText(UiLanguage.t("Não foi possível listar LoRAs. Confira a conexão.")));}
        });
    }

    private Button loraChip(VersionViewer owner) {
        Button chip=button(UiLanguage.t("LoRAs"),FIELD,TEXT);chip.setTextSize(12);chip.setPadding(dp(8),0,dp(8),0);chip.setSingleLine(true);
        chip.setContentDescription(UiLanguage.t("Configurar LoRAs"));
        if(owner!=null){chip.setOnClickListener(v->{hideKeyboard(owner.instruction);UiMotion.hide(owner.optionPanel);openLoras(owner);});}else chip.setOnClickListener(v->{hideKeyboard(dockPrompt);UiMotion.hide(quickOptions);openLoras();});
        return chip;
    }

    private void showLoraEditor(List<String> ids,List<String> installed,String key,JSONObject template,VersionViewer owner) {
        try {
            final JSONObject draft=new JSONObject(getPreferences(0).getString(key,LoraSupport.defaults(template,ids).toString()));if(hideAllNsfw)LoraPrivacy.disableChoices(draft);
            // Sort from the saved selection once per opening; rows stay put while editing/searching.
            final Map<String,java.util.Set<String>> selectedAtOpen=new HashMap<>();
            for(String id:ids){
                java.util.Set<String> selected=new java.util.HashSet<>();JSONArray entries=draft.optJSONArray(id);
                if(entries!=null)for(int i=0;i<entries.length();i++){JSONObject entry=entries.optJSONObject(i);if(entry!=null&&entry.optBoolean("on"))selected.add(entry.optString("lora"));}
                selectedAtOpen.put(id,selected);
            }
            LinearLayout sheet=column();sheet.setPadding(dp(12),dp(12),dp(12),dp(12));sheet.setBackground(FlowDesign.shape(this,FlowDesign.SURFACE,30,true));
            LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);header.addView(text(UiLanguage.t("LoRAs"),20,TEXT,true),new LinearLayout.LayoutParams(0,dp(48),1));
            Button reset=button("",Color.TRANSPARENT,MUTED);FlowDesign.symbol(reset,"refresh",UiLanguage.t("Restaurar LoRAs do workflow"));header.addView(reset,new LinearLayout.LayoutParams(dp(48),dp(48)));
            Button done=button(UiLanguage.t("Aplicar"),ACCENT,BG);header.addView(done,new LinearLayout.LayoutParams(dp(88),dp(48)));sheet.addView(header);
            TextView note=text("",12,MUTED,false);collapseEmpty(note);sheet.addView(note);
            LinearLayout layers=optionRow(sheet);final int[] selectedLayer={0};
            EditText search=new EditText(this);styleInput(search);search.setSingleLine(true);search.setHint(UiLanguage.t("Buscar LoRA"));search.setTextSize(14);search.setCompoundDrawables(FlowDesign.icon(this,"search",19),null,null,null);search.setCompoundDrawablePadding(dp(10));search.setBackground(round(BG,18,0));LinearLayout.LayoutParams searchLayout=new LinearLayout.LayoutParams(-1,dp(48));searchLayout.setMargins(0,dp(8),0,dp(12));sheet.addView(search,searchLayout);
            ScrollView scroll=new ScrollView(this);LinearLayout list=column();scroll.addView(list);sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            Dialog dialog=styleSheet(sheet);dialog.getWindow().setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*.76f));
            final Runnable[] redraw={null};
            redraw[0]=()->{
                layers.removeAllViews();for(int i=0;i<ids.size();i++){final int layer=i;segment(layers,UiLanguage.t("Camada ")+(i+1),null,i==selectedLayer[0],UiLanguage.t("Camada de LoRA ")+(i+1)).setOnClickListener(v->{selectedLayer[0]=layer;redraw[0].run();});}layers.setVisibility(ids.size()>1?View.VISIBLE:View.GONE);
                list.removeAllViews();String id=ids.get(selectedLayer[0]);String family=LoraSupport.family(template,id,new java.util.HashSet<>());JSONArray entries=draft.optJSONArray(id);
                if(entries==null)return;
                List<String> paths=new ArrayList<>();for(String path:installed)if(LoraSupport.matches(path,family)&&!LoraSupport.structural(template,path)&&(!hideAllNsfw||!LoraPrivacy.restricted(path)))paths.add(path);
                for(int i=0;i<entries.length();i++){String path=entries.optJSONObject(i).optString("lora");if(entries.optJSONObject(i).optBoolean("on")&&!paths.contains(path)&&(!hideAllNsfw||!LoraPrivacy.restricted(path)))paths.add(path);}
                java.util.Set<String> selected=selectedAtOpen.get(id);
                Collections.sort(paths,(a,b)->{int group=Boolean.compare(selected.contains(b),selected.contains(a));return group!=0?group:String.CASE_INSENSITIVE_ORDER.compare(a,b);});
                String filter=search.getText().toString().toLowerCase(Locale.ROOT);int count=0;
                for(String path:paths){if(hideAllNsfw&&LoraPrivacy.restricted(path))continue;if(!LoraPrivacy.matchesSearch(path,filter))continue;count++;
                    JSONObject found=null;for(int i=0;i<entries.length();i++)if(path.equals(entries.optJSONObject(i).optString("lora")))found=entries.optJSONObject(i);
                    try{if(found==null){found=new JSONObject().put("lora",path).put("on",false).put("strength",1.0);entries.put(found);}}catch(Exception ignored){continue;}
                    final JSONObject entry=found;LinearLayout card=column();card.setPadding(dp(14),dp(10),dp(14),dp(10));
                    Runnable style=()->card.setBackground(FlowDesign.shape(this,entry.optBoolean("on")?0xFF25272B:0xFF191A1C,20,entry.optBoolean("on")));style.run();
                    LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);LinearLayout labels=column();TextView name=text(LoraPrivacy.display(path),15,TEXT,true);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(name);
                    TextView file=text(LoraPrivacy.filename(path),12,MUTED,false);file.setMaxLines(2);file.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);file.setPadding(0,dp(4),dp(6),0);labels.addView(file);line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
                    Button rename=button("",Color.TRANSPARENT,MUTED);FlowDesign.symbol(rename,"edit",UiLanguage.t("Renomear LoRA")+": "+LoraPrivacy.display(path));line.addView(rename,new LinearLayout.LayoutParams(dp(44),dp(48)));
                    android.widget.Switch enabled=new android.widget.Switch(this);enabled.setChecked(entry.optBoolean("on"));enabled.setContentDescription(UiLanguage.t("Ativar LoRA ")+path);line.addView(enabled,new LinearLayout.LayoutParams(dp(52),dp(48)));card.addView(line);
                    enabled.setOnCheckedChangeListener((v,on)->{try{entry.put("on",on);style.run();}catch(Exception ignored){}});
                    View.OnClickListener editName=v->renameLora(path,()->{int position=scroll.getScrollY();redraw[0].run();scroll.post(()->scroll.scrollTo(0,position));});rename.setOnClickListener(editName);name.setOnClickListener(editName);
                    card.setOnLongClickListener(v->{new FlowDialogs.Builder(this).setTitle(LoraPrivacy.display(path)).setMessage(path+"\n\n"+LoraPrivacy.label(path)).setPositiveButton(UiLanguage.t("OK"),null).show();return true;});
                    LinearLayout weight=row();weight.setGravity(Gravity.CENTER_VERTICAL);TextView weightLabel=text(UiLanguage.t("Peso"),12,MUTED,false);weightLabel.setGravity(Gravity.CENTER_VERTICAL);weight.addView(weightLabel,new LinearLayout.LayoutParams(0,dp(44),1));weight.addView(loraStepper(entry,"strength",path),new LinearLayout.LayoutParams(dp(132),dp(44)));card.addView(weight);
                    if(entry.has("strengthTwo")){LinearLayout clip=row();clip.setGravity(Gravity.CENTER_VERTICAL);TextView clipLabel=text(UiLanguage.t("CLIP"),12,MUTED,false);clipLabel.setGravity(Gravity.CENTER_VERTICAL);clip.addView(clipLabel,new LinearLayout.LayoutParams(0,dp(44),1));clip.addView(loraStepper(entry,"strengthTwo",path+" CLIP"));card.addView(clip);}
                    if(!installed.contains(path))card.addView(text(UiLanguage.t("Arquivo indisponível"),12,0xFFFFB4AB,false));list.addView(card);gap(list,10);
                }
                if(count==0){TextView empty=text(UiLanguage.t("Nenhum LoRA compatível encontrado."),14,MUTED,false);empty.setPadding(dp(8),dp(20),dp(8),dp(20));list.addView(empty);}
            };
            search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(android.text.Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){redraw[0].run();}});
            done.setOnClickListener(v->{try{
                if(!key.equals(loraContextKey(owner)))throw new IllegalStateException(UiLanguage.t("Workflow mudou. Abra LoRAs novamente."));
                // Keep custom weights when disabled, without persisting every untouched installed file.
                JSONObject selected=new JSONObject();for(String id:ids){JSONArray active=new JSONArray(),entries=draft.getJSONArray(id);for(int i=0;i<entries.length();i++){JSONObject entry=entries.getJSONObject(i);if(entry.optBoolean("on")||Math.abs(entry.optDouble("strength",1)-1)>.00001||entry.has("strengthTwo"))active.put(entry);}selected.put(id,active);}
                if(hideAllNsfw)LoraPrivacy.disableChoices(selected);LoraSupport.apply(new JSONObject(template.toString()),selected,installed);
                if(!getPreferences(0).edit().putString(key,selected.toString()).commit())throw new IllegalStateException(UiLanguage.t("Falha ao salvar."));updateLoraButton();if(owner!=null){updateViewerLoras(owner);owner.saveEditDraft();}dialog.dismiss();
            }catch(Exception e){note.setText(e.getMessage());}});
            reset.setOnClickListener(v->{getPreferences(0).edit().remove(key).apply();updateLoraButton();if(owner!=null){updateViewerLoras(owner);owner.saveEditDraft();}dialog.dismiss();openLoras(owner);});redraw[0].run();
        }catch(Exception e){Toast.makeText(this,UiLanguage.t("Falha ao abrir LoRAs: ")+e.getMessage(),Toast.LENGTH_LONG).show();}
    }

    private void renameLora(String path,Runnable refresh){
        LinearLayout sheet=column();sheet.setPadding(dp(20),dp(16),dp(20),dp(20));sheet.setBackground(FlowDesign.shape(this,FlowDesign.SURFACE,30,true));
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);header.addView(text(UiLanguage.t("Renomear LoRA"),20,TEXT,true),new LinearLayout.LayoutParams(0,dp(48),1));Button close=button("",Color.TRANSPARENT,MUTED);FlowDesign.symbol(close,"close",UiLanguage.t("Cancelar"));header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));sheet.addView(header);
        TextView file=text(LoraPrivacy.filename(path),12,MUTED,false);file.setMaxLines(3);file.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);sheet.addView(file);gap(sheet,16);
        EditText name=new EditText(this);styleInput(name);name.setSingleLine(true);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(180)});name.setHint(UiLanguage.t("Nome do LoRA"));name.setContentDescription(UiLanguage.t("Nome do LoRA"));name.setText(LoraPrivacy.display(path));name.setSelectAllOnFocus(true);name.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);sheet.addView(name,new LinearLayout.LayoutParams(-1,dp(52)));
        gap(sheet,8);sheet.addView(text(UiLanguage.t("Deixe vazio para usar o nome automático."),12,MUTED,false));gap(sheet,16);
        Button save=button(UiLanguage.t("Salvar"),ACCENT,BG);sheet.addView(save,new LinearLayout.LayoutParams(-1,dp(48)));
        Dialog dialog=styleSheet(sheet);dialog.getWindow().setLayout(-1,-2);dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);close.setOnClickListener(v->dialog.dismiss());
        save.setOnClickListener(v->{try{JSONObject names=new JSONObject(getPreferences(0).getString(LoraPrivacy.NAMES_KEY,"{}"));String value=LoraPrivacy.cleanName(name.getText().toString()),id=LoraSupport.normalized(path);if(value.isEmpty()||value.equals(LoraPrivacy.automaticName(path)))names.remove(id);else names.put(id,value);
            if(!getPreferences(0).edit().putString(LoraPrivacy.NAMES_KEY,names.toString()).commit())throw new IllegalStateException();LoraPrivacy.loadNames(names);refresh.run();dialog.dismiss();
        }catch(Exception e){name.setError(UiLanguage.t("Falha ao salvar."));}});
        name.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){save.performClick();return true;}return false;});
    }

    private LinearLayout loraStepper(JSONObject entry,String key,String name) {
        LinearLayout steps=row();steps.setGravity(Gravity.CENTER_VERTICAL);steps.setBackground(FlowDesign.shape(this,FlowDesign.CONTROL,12,false));
        Button minus=button("−",Color.TRANSPARENT,TEXT),plus=button("+",Color.TRANSPARENT,TEXT);minus.setTextSize(20);plus.setTextSize(20);
        minus.setContentDescription(UiLanguage.t("Diminuir peso de ")+name);plus.setContentDescription(UiLanguage.t("Aumentar peso de ")+name);
        TextView value=text("",13,TEXT,true);value.setGravity(Gravity.CENTER);value.setContentDescription(UiLanguage.t("Peso de ")+name);
        steps.addView(minus,new LinearLayout.LayoutParams(dp(44),dp(44)));steps.addView(value,new LinearLayout.LayoutParams(dp(44),dp(44)));steps.addView(plus,new LinearLayout.LayoutParams(dp(44),dp(44)));
        Runnable render=()->{double weight=entry.optDouble(key,1);value.setText(String.format(Locale.ROOT,UiLanguage.t("%.1f"),weight));minus.setEnabled(weight> -20);plus.setEnabled(weight<20);};
        minus.setOnClickListener(v->{try{entry.put(key,Math.max(-20,Math.round((entry.optDouble(key,1)-.1)*10)/10.0));render.run();}catch(Exception ignored){}});
        plus.setOnClickListener(v->{try{entry.put(key,Math.min(20,Math.round((entry.optDouble(key,1)+.1)*10)/10.0));render.run();}catch(Exception ignored){}});render.run();return steps;
    }

    private void runWorkflow() {
        if(VideoWorkflow.matches(workflow)){submitVideo(null);return;}
        if (!connected || baseUrl == null || !baseUrl.equals(
                serverInput.getText().toString().trim().replaceAll("/+$", ""))) {
            result.setText(UiLanguage.t("Conecte ao servidor primeiro."));
            workflowDetails.setVisibility(View.VISIBLE); connectionPanel.setVisibility(View.VISIBLE);
            openSettings();
            return;
        }
        if (workflow == null) { result.setText(UiLanguage.t("Importe um workflow API primeiro.")); openSettings(); return; }
        JSONObject copy;
        String idea = "";
        final String draftPrompt = dockPrompt.getText().toString().trim();
        final List<ReferencePreset> presets=taggedReferences(draftPrompt);
        if (hasIdeaField() && draftPrompt.isEmpty()) { result.setText(UiLanguage.t("Escreva sua ideia primeiro.")); return; }
        try {
            copy = new JSONObject(workflow.toString());
            for (Field field : fields) {
                Object value;
                if (field.toggle != null) value = field.toggle.isChecked();
                else if (field.spinner != null) value = field.spinner.getSelectedItem().toString();
                else if (field.original instanceof Integer || field.original instanceof Long)
                    value = Long.parseLong(field.edit.getText().toString().trim());
                else if (field.original instanceof Number)
                    value = Double.parseDouble(field.edit.getText().toString().trim());
                else value = field.edit.getText().toString();
                if ("Ideia da imagem".equals(field.primaryLabel)) {
                    String raw=String.valueOf(value).trim();
                    value=PromptTags.expand(raw,tagDescriptions());
                }
                copy.getJSONObject(field.nodeId).getJSONObject("inputs").put(field.key, value);
                if ("Ideia da imagem".equals(field.primaryLabel)) idea = String.valueOf(value).trim();
            }
        } catch (Exception e) { result.setText(UiLanguage.t("Campo inválido: ") + e.getMessage()); return; }
        if (hasIdeaField() && idea.isEmpty()) { result.setText(UiLanguage.t("Escreva sua ideia primeiro.")); return; }
        final List<Generation> referenceImages;
        try{referenceImages=mainReferences.snapshot();if(ReferenceSupport.klein(copy)&&referenceImages.isEmpty()&&ReferenceBindings.pictures(presets,false,baseUrl).isEmpty())throw new IllegalArgumentException(UiLanguage.t("Adicione uma imagem em + ou use uma tag com imagem para usar Klein."));
            if(GenerationOptions.supportsImageResolution(copy)){
                Generation ref=referenceImages.isEmpty()?null:referenceImages.get(0);if(ref!=null)galleryAspect(ref);
                int width=ref==null?0:ref.mediaWidth,height=ref==null?0:ref.mediaHeight;
                if(ref==null){List<ReferencePreset.Picture> pictures=ReferenceBindings.pictures(presets,false,baseUrl);if(!pictures.isEmpty()){BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;VaultFiles.bitmap(referenceStore.file(pictures.get(0).file).getAbsolutePath(),bounds);width=bounds.outWidth;height=bounds.outHeight;}}
                GenerationOptions.imageOutput(copy,currentRatio(),currentImageMegapixels(),width,height);
            }
        }catch(Exception e){result.setText(e.getMessage());return;}
        final String promptText = idea.isEmpty() ? extractPrompt(copy) : idea;
        final String name = workflowName;
        final String address = baseUrl;
        final boolean hideInFeed = !hideAllNsfw || referenceImages.stream().anyMatch(item->item.nsfw)||ReferenceBindings.hidden(presets);
        final String savedLoras = getPreferences(0).getString(loraKey(), "");
        final int requested = batchCount;final Destination destination=new Destination(null);final String draftContext=mainDraftKey();
        final String advanced=advancedValue(workflow,workflowName);
        final JSONObject recipe;try{recipe=captureRecipe(null,copy,"image",referenceImages,presets,draftPrompt,promptText,hideInFeed);}catch(Exception e){result.setText(UiLanguage.t("Falha ao guardar configuração: ")+e.getMessage());return;}
        runButton.setEnabled(false);
        updateGenerationStatus();
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(dockPrompt.getWindowToken(), 0);
        result.setText(UiLanguage.t("Enviando para ComfyUI..."));
        worker.execute(() -> {
            int accepted=0;
            try {
                List<String> uploadedReferences=new ArrayList<>();for(Generation ref:referenceImages)uploadedReferences.add(uploadEditInput(address,imageFile(ref.id)));
                if (!savedLoras.isEmpty()) applySelectedLoras(copy,savedLoras,address);
                applyReferenceAssets(copy,presets,address,uploadedReferences,referenceImages);
                GenerationRecipe.preserveOutput(copy,recipe);AdvancedGeneration.apply(copy,new JSONObject(advanced));prepareAgent(copy,recipe,address);
                for(int index=0;index<requested;index++) {
                    JSONObject graph=new JSONObject(copy.toString());GenerationOptions.varySeed(graph,index);
                    JSONObject body=new JSONObject().put("prompt",graph).put("client_id",UUID.randomUUID().toString());
                    String id=requestJson("POST",address+"/prompt",body).optString("prompt_id");
                    if(!validId(id))throw new IllegalStateException(UiLanguage.t("Resposta sem ID de geração."));
                    saveRecipe(id,recipe,graph,uploadedReferences);
                    accepted++;final int number=accepted;
                    runOnUiThread(()->{
                        Generation item=new Generation();item.id=id;item.serverAddress=address;item.workflowName=name;
                        item.prompt=promptText;item.promptTemplate=draftPrompt;item.nsfw=hideInFeed;item.state="queued";item.createdAt=System.currentTimeMillis();
                        item.settings=GenerationDetails.snapshot(graph);destination.apply(item,null);
                        generations.add(0,item);saveFeed();renderFeed();result.setText(number+" / "+requested+UiLanguage.t(" enviados"));
                    });
                }
                runOnUiThread(()->{
                    if(draftContext.equals(mainDraftKey())){if(destination.session.isEmpty()&&dockPrompt.getText().toString().trim().equals(draftPrompt))dockPrompt.setText("");saveDraft();}
                    UiMotion.hide(quickOptions);
                    if(settingsDialog!=null)settingsDialog.dismiss();
                    mainScroll.post(()->mainScroll.scrollTo(0,0));gallery.smoothScrollToPosition(0);
                });
            }catch(Exception e){final int count=accepted;runOnUiThread(()->new FlowDialogs.Builder(this).setTitle(UiLanguage.t("Lote interrompido")).setMessage(count+UiLanguage.t(" de ")+requested+UiLanguage.t(" envios confirmados. Os itens enviados foram preservados.\n\n")+e.getMessage()+UiLanguage.t("\n\nConfira a fila antes de reenviar para evitar duplicatas.")).setPositiveButton(UiLanguage.t("Entendi"),null).show());}
            finally{runOnUiThread(()->{runButton.setEnabled(true);updateGenerationStatus();});}
        });
    }

    private boolean hasIdeaField() {
        for (Field field : fields) if ("Ideia da imagem".equals(field.primaryLabel)) return true;
        return false;
    }

    private String extractPrompt(JSONObject graph) {
        Iterator<String> ids = graph.keys();
        while (ids.hasNext()) {
            JSONObject node = graph.optJSONObject(ids.next());
            if (node == null) continue;
            JSONObject inputs = node.optJSONObject("inputs");
            if (inputs == null) continue;
            if ("PrimitiveStringMultiline".equals(node.optString("class_type"))) {
                String value = inputs.optString("value");
                if (!value.isEmpty() && value.length() < 500) return value;
            }
        }
        return "Imagem gerada";
    }

    private boolean updateProgressQueue(String address,JSONObject queueState){
        boolean changed=false;String watchId="",client="";long now=System.currentTimeMillis();
        JSONArray running=queueState.optJSONArray("queue_running"),waiting=queueState.optJSONArray("queue_pending");
        for(Generation item:generations){
            if(!("running".equals(item.state)||"queued".equals(item.state))||!address.equals(item.serverAddress))continue;
            item.progress.serverSeenAt=now;item.progressDisplayAt=0;
            item.progress.queuePosition=GenerationProgress.queuePosition(waiting,item.id);
            if(running!=null)for(int i=0;i<running.length();i++){
                JSONArray entry=running.optJSONArray(i);if(entry==null||!item.id.equals(entry.optString(1)))continue;
                if(!"running".equals(item.state)){item.state="running";item.progress.observedStart=now;changed=true;}
                item.progress.queuePosition=0;item.progress.nodes=GenerationProgress.nodeTypes(entry.optJSONObject(2));
                JSONObject extra=entry.optJSONObject(3);String id=extra==null?"":extra.optString("client_id");
                if(watchId.isEmpty()&&!id.isEmpty()){watchId=item.id;client=id;}
            }
        }
        if(appResumed&&!watchId.isEmpty())watchProgress(address,client,watchId);else stopProgressSocket();
        return changed;
    }
    private void watchProgress(String address,String client,String id){
        if(progressSocket!=null&&progressSocket.address.equals(address)&&progressSocket.clientId.equals(client)&&progressJob.equals(id)&&!progressSocket.ended)return;
        long now=System.currentTimeMillis();if(now<progressRetryAt)return;
        stopProgressSocket();progressJob=id;int epoch=progressEpoch;progressRetryAt=now+5000;
        try{progressSocket=new ComfyProgressSocket(this,address,client,new ComfyProgressSocket.Listener(){
            public void connected(boolean live){runOnUiThread(()->{if(epoch!=progressEpoch)return;Generation item=findGeneration(id);if(item!=null){item.progress.streamLive=live;item.progressDisplayAt=0;}});}
            public void message(JSONObject message){runOnUiThread(()->{
                if(epoch!=progressEpoch||!address.equals(baseUrl))return;
                JSONObject data=message.optJSONObject("data");if(data==null)return;
                String promptId=data.optString("prompt_id",id);if(!id.equals(promptId))return;
                Generation item=findGeneration(id);if(item==null||!("running".equals(item.state)||"queued".equals(item.state)))return;
                String type=message.optString("type");if(!type.equals("executing")&&!type.equals("progress")&&!type.equals("progress_state")&&!type.equals("execution_start")&&!type.equals("execution_success"))return;
                item.progress.event(type,data,System.currentTimeMillis());item.progressDisplayAt=0;
                if(type.equals("execution_start"))saveFeed();
            });}
        });}catch(Exception ignored){progressSocket=null;}
    }
    private void stopProgressSocket(){
        progressEpoch++;Generation item=findGeneration(progressJob);if(item!=null){item.progress.streamLive=false;item.progressDisplayAt=0;}
        if(progressSocket!=null)progressSocket.close();progressSocket=null;progressJob="";
    }
    private GenerationProgress.Display progressDisplay(Generation item){
        long now=System.currentTimeMillis();if(item.progressDisplay!=null&&now-item.progressDisplayAt<1000)return item.progressDisplay;
        List<Long> durations=new ArrayList<>();
        for(Generation previous:generations){
            long duration=previous.generationFinishedAt-previous.generationStartedAt;
            if(previous==item||!"done".equals(previous.state)||previous.generationStartedAt<=0||duration<2000||duration>12*3600_000L)continue;
            if(!previous.serverAddress.equals(item.serverAddress)||!previous.workflowName.equals(item.workflowName)||!previous.mediaKind.equals(item.mediaKind)||previous.parentId.isEmpty()!=item.parentId.isEmpty())continue;
            if(GenerationProgress.comparable(item.settings,previous.settings)){durations.add(duration);if(durations.size()==9)break;}
        }
        boolean online=baseUrl!=null&&baseUrl.equals(item.serverAddress)&&connected&&(item.progress.serverSeenAt==0||now-item.progress.serverSeenAt<15000);
        item.progressDisplay=item.progress.display(item.state,now,GenerationProgress.median(durations),online);item.progressDisplayAt=now;return item.progressDisplay;
    }
    private final class GenerationProgressView extends LinearLayout {
        final Generation item;final TextView title,detail;final ProgressBar bar;
        final Runnable tick=new Runnable(){public void run(){if(isShown()&&appResumed){GenerationProgress.Display display=progressDisplay(item);title.setText(display.title);detail.setText(display.detail);bar.setVisibility(display.fraction<0?View.GONE:View.VISIBLE);bar.setProgress(Math.round(Math.max(0,display.fraction)*1000));}postDelayed(this,1000);}};
        GenerationProgressView(Generation item,boolean compact){
            super(MainActivity.this);this.item=item;setOrientation(VERTICAL);setGravity(Gravity.CENTER);setPadding(dp(compact?12:24),dp(12),dp(compact?12:24),dp(12));
            title=text("",compact?12:15,TEXT,true);title.setGravity(Gravity.CENTER);addView(title);
            detail=text("",compact?11:12,MUTED,false);detail.setGravity(Gravity.CENTER);LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(-1,-2);d.topMargin=dp(6);addView(detail,d);
            bar=new ProgressBar(MainActivity.this,null,android.R.attr.progressBarStyleHorizontal);bar.setIndeterminate(false);bar.setMax(1000);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFC5B7F6));bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x28FFFFFF));LinearLayout.LayoutParams b=new LinearLayout.LayoutParams(-1,dp(4));b.topMargin=dp(12);addView(bar,b);
        }
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();tick.run();}
        @Override protected void onDetachedFromWindow(){removeCallbacks(tick);super.onDetachedFromWindow();}
    }

    private void poll() {
        if (!polling || baseUrl == null) return;
        final String address = baseUrl;
        List<String> pending = new ArrayList<>();
        for (Generation item : generations) {
            if (("queued".equals(item.state) || "running".equals(item.state)) && (item.serverAddress.isEmpty() || address.equals(item.serverAddress))) pending.add(item.id);
        }
        worker.execute(() -> {
            try {
                JSONObject state = getJson(address + "/queue");
                JSONArray waiting = state.optJSONArray("queue_pending");
                JSONArray running = state.optJSONArray("queue_running");
                int p = waiting == null ? 0 : waiting.length();
                int r = running == null ? 0 : running.length();
                runOnUiThread(() -> {
                    if (!address.equals(baseUrl)) return;
                    if (!connected) {
                        connected = true; connection.setText(UiLanguage.t("● Conectado")); connection.setTextColor(MINT);
                        getPreferences(0).edit().putString("server", address).apply();
                        result.setText(UiLanguage.t("Conexão restabelecida. Verificando resultados…"));
                    }
                    queue.setText(r + UiLanguage.t(" gerando · ") + p + UiLanguage.t(" na fila"));
                    boolean changed=updateProgressQueue(address,state);
                    int active=pending.size();
                    if(changed){saveFeed();renderFeed();}
                    if (active > 0 && runButton.isEnabled())
                        result.setText(active == 1 ? UiLanguage.t("Preparando sua geração…") : active + UiLanguage.t(" gerações em andamento…"));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!address.equals(baseUrl)) return;
                    connected = false; connection.setText(UiLanguage.t("● Reconectando")); connection.setTextColor(MUTED);
                    queue.setText(UiLanguage.t("Sem conexão · nova tentativa automática"));
                    if (!pending.isEmpty()) result.setText(UiLanguage.t("Servidor indisponível. Retomando quando conectar…"));
                });
            }
            for (String id : pending) {
                try { inspectResult(address, id); }
                catch (Exception ignored) { }
            }
            if (polling) handler.postDelayed(this::poll, 3500);
        });
    }

    private void inspectResult(String address, String id) throws Exception {
        if(generationStorage.deleted(id))return;
        JSONObject history = getJson(address + "/history/" + URLEncoder.encode(id, "UTF-8"));
        JSONObject job = history.optJSONObject(id);
        if (job == null) {
            // A successful history read AND a fresh queue read are required before declaring a lost job.
            JSONObject queueState = getJson(address + "/queue");
            if (!queueState.has("queue_pending") || !queueState.has("queue_running")) return;
            String key = address + "|" + id;
            for (String list : new String[]{"queue_pending", "queue_running"}) {
                JSONArray entries = queueState.getJSONArray(list);
                for (int i = 0; i < entries.length(); i++) {
                    JSONArray entry = entries.optJSONArray(i);
                    if (entry != null && id.equals(entry.optString(1))) { missingJobs.remove(key); return; }
                }
            }
            long now = android.os.SystemClock.elapsedRealtime();
            Long since = missingJobs.get(key);
            if (since == null) { missingJobs.put(key, now); return; }
            if (now - since >= 30000) runOnUiThread(() -> {
                if (address.equals(baseUrl)) finishGeneration(id, "error", null,
                        "Geração ausente no servidor. Use ⋮ para verificar resultado ou reutilizar prompt.");
            });
            return;
        }
        missingJobs.remove(address + "|" + id);
        runOnUiThread(()->{Generation item=findGeneration(id);if(item!=null&&applyGenerationDetails(item,job))saveFeed();});
        JSONObject status = job.optJSONObject("status");
        if (status == null) return;
        if ("error".equals(status.optString("status_str"))) {
            runOnUiThread(() -> finishGeneration(id, "error", null, "Geração falhou."));
            return;
        }
        if (!status.optBoolean("completed")) return;
        if(generationStorage.deleted(id))return;
        JSONObject image = firstImage(job);
        if (image == null) {
            runOnUiThread(() -> finishGeneration(id, "error", null, "Concluído sem imagem ou vídeo."));
            return;
        }
        runOnUiThread(()->{Generation item=findGeneration(id);if(item!=null){item.progress.downloading=true;item.progressDisplayAt=0;}});
        String key = address + "|" + id;
        try {
            if (isVideoOutput(image)) saveVideo(address + imagePath(image), id);
            else {
                byte[] bytes = requestBytes("GET", address + imagePath(image), null, 30_000_000);
                saveImage(id, bytes);
            }
            downloadFailures.remove(key);
            runOnUiThread(() -> finishGeneration(id, "done", image, ""));
        } catch (Exception e) {
            int attempts = downloadFailures.containsKey(key) ? downloadFailures.get(key) + 1 : 1;
            downloadFailures.put(key, attempts);
            if (attempts >= 3) runOnUiThread(() -> finishGeneration(id, "error", null,
                    "Resultado pronto, mas não foi possível baixar. Use Verificar resultado para tentar novamente."));
            else runOnUiThread(() -> result.setText(UiLanguage.t("Resultado pronto. Tentando baixar novamente…")));
        }
    }

    private void finishGeneration(String id, String state, JSONObject image, String error) {
        Generation item = findGeneration(id);
        if (item == null || "done".equals(item.state)) return;
        item.state = state;
        if(id.equals(progressJob))stopProgressSocket();
        item.error = error;
        if(item.temporary&&item.expiresAt==0&&("done".equals(state)||"error".equals(state)))item.expiresAt=System.currentTimeMillis()+TrialPolicy.RETENTION;
        if (image != null) {
            item.filename = image.optString("filename");
            item.subfolder = image.optString("subfolder");
            item.imageType = image.optString("type", "output");
            item.mediaKind = isVideoOutput(image) ? "video" : "image";
            item.mediaWidth=0;item.mediaHeight=0;galleryAspect(item);
            if(!familyId(item).equals(item.rootId)&&!item.rootId.isEmpty()){item.sourceId=item.parentId;item.rootId=item.id;item.parentId="";item.editNumber=0;}
        }
        saveFeed();
        renderFeed();
        result.setText("done".equals(state) ? ("video".equals(item.mediaKind) ? UiLanguage.t("Vídeo pronto no feed.") : UiLanguage.t("Imagem pronta no feed.")) : error);
    }

    private JSONObject firstImage(JSONObject job) {
        JSONObject outputs = job.optJSONObject("outputs");
        if (outputs == null) return null;
        // Prefer a rendered video to intermediate image previews in mixed workflows.
        Iterator<String> ids = outputs.keys();
        JSONObject firstStill = null;
        while (ids.hasNext()) {
            JSONObject output = outputs.optJSONObject(ids.next());
            if (output == null) continue;
            for (String key : new String[]{"images", "gifs", "videos"}) {
                JSONArray images = output.optJSONArray(key);
                if (images == null) continue;
                for (int i = 0; i < images.length(); i++) {
                    JSONObject file = images.optJSONObject(i);
                    if (file == null) continue;
                    if (isVideoOutput(file)) return file;
                    if ("images".equals(key) && firstStill == null) firstStill = file;
                }
            }
        }
        return firstStill;
    }

    private boolean isVideoOutput(JSONObject file) {
        return file.optString("filename").toLowerCase(Locale.ROOT).matches(".*\\.(mp4|webm|mkv|mov)$");
    }

    private File videoFile(String id) { return new File(imageFile(id).getParentFile(), id + ".mp4"); }

    private void saveVideo(String address, String id) throws Exception {
        File target = videoFile(id), partial = new File(target.getPath() + ".part");
        HttpURLConnection connection = DesktopConnection.open(this,address);
        connection.setConnectTimeout(10000); connection.setReadTimeout(60000);
        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
        android.media.MediaDataSource vaultSource=null;
        Bitmap frame = null, thumb = null;
        try {
            int code = connection.getResponseCode();
            if(code>=300&&code<400)throw new IOException(UiLanguage.t("Redirecionamento de mídia recusado."));
            if (code >= 400) throw new IllegalStateException(UiLanguage.t("Download do vídeo: HTTP ") + code);
            try (InputStream input = connection.getInputStream(); OutputStream output = VaultFiles.output(partial)) {
                byte[] buffer = new byte[65536]; long total = 0; int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 512L * 1024 * 1024) throw new IllegalStateException(UiLanguage.t("Vídeo maior que 512 MB."));
                    output.write(buffer, 0, count);
                }
            }
            vaultSource=VaultFiles.media(partial);retriever.setDataSource(vaultSource);
            frame = android.os.Build.VERSION.SDK_INT >= 27
                    ? retriever.getScaledFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 720, 720)
                    : retriever.getFrameAtTime(0);
            if (frame == null) throw new IllegalStateException(UiLanguage.t("Vídeo sem quadro legível."));
            int width = Math.min(frame.getWidth(), 720), height = Math.max(1, frame.getHeight() * width / frame.getWidth());
            thumb = Bitmap.createScaledBitmap(frame, width, height, true);
            try (OutputStream output = VaultFiles.output(thumbFile(id))) {
                if (!thumb.compress(Bitmap.CompressFormat.JPEG, 85, output)) throw new IllegalStateException(UiLanguage.t("Falha na capa do vídeo."));
            }
            retriever.release(); retriever = null;
            if (!VaultFiles.renameVideo(partial,target)) throw new IllegalStateException(UiLanguage.t("Falha ao salvar vídeo."));
        } catch (Exception e) {
            VaultFiles.delete(partial); VaultFiles.delete(thumbFile(id)); throw e;
        } finally {
            connection.disconnect(); if (retriever != null) retriever.release();VaultFiles.close(vaultSource);
            if (thumb != null && thumb != frame) thumb.recycle(); if (frame != null) frame.recycle();
        }
    }

    private String imagePath(JSONObject image) throws Exception {
        return "/view?filename=" + URLEncoder.encode(image.optString("filename"), "UTF-8")
                + "&subfolder=" + URLEncoder.encode(image.optString("subfolder"), "UTF-8")
                + "&type=" + URLEncoder.encode(image.optString("type", "output"), "UTF-8");
    }

    private void refreshPending() {
        if (baseUrl == null) { renderFeed(); return; }
        final String address = baseUrl;
        List<String> pending = new ArrayList<>();
        for (Generation item : generations) {
            if (("queued".equals(item.state) || "running".equals(item.state)) && (item.serverAddress.isEmpty() || address.equals(item.serverAddress))) pending.add(item.id);
        }
        worker.execute(() -> {
            for (String id : pending) {
                try { inspectResult(address, id); }
                catch (Exception ignored) { }
            }
            runOnUiThread(this::renderFeed);
        });
    }

    private Generation findGeneration(String id) {
        if(generationStorage!=null&&generationStorage.deleted(id))return null;
        for (Generation item : generations) if (id.equals(item.id)) return item;
        return restoredInputs.get(id);
    }

    private void loadFeed() {
        if(!new File(getFilesDir(),"feed.json").exists()&&!new File(getFilesDir(),"feed.json.bak").exists())return;
        try (InputStream input = VaultFiles.open(new File(getFilesDir(), "feed.json"))) {
            JSONArray array = new JSONArray(new String(read(input, 2_000_000), StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                Generation item = Generation.fromJson(array.getJSONObject(i));
                if (validId(item.id)&&!generationStorage.deleted(item.id)) generations.add(item);
            }
        } catch (Exception error) { throw new IllegalStateException(UiLanguage.t("Biblioteca indisponível. Dados preservados."),error); }
    }

    private boolean saveFeed() {
        try {
            JSONArray array = new JSONArray();
            for (Generation item : generations) array.put(item.toJson());
            VaultFiles.write(new File(getFilesDir(), "feed.json"),array.toString().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (Exception ignored) { return false; }
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,128}");
    }

    private File imageFile(String id) {
        File directory = new File(getFilesDir(), "images");
        if (!directory.exists()) directory.mkdirs();
        return new File(directory, id + ".img");
    }

    private File thumbFile(String id) {
        File directory = new File(getFilesDir(), "images");
        if (!directory.exists()) directory.mkdirs();
        return new File(directory, id + ".jpg");
    }

    private void saveImage(String id, byte[] bytes) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0)
            throw new IllegalStateException(UiLanguage.t("Imagem inválida."));
        options.inJustDecodeBounds = false;
        options.inSampleSize = Math.max(1,
                (Math.max(options.outWidth, options.outHeight) + 1599) / 1600);
        Bitmap sampled = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        if (sampled == null) throw new IllegalStateException(UiLanguage.t("Imagem inválida."));
        sampled=orientBitmap(sampled,imageOrientation(new java.io.ByteArrayInputStream(bytes)));
        Bitmap thumb = null;
        try {
            int width = Math.min(sampled.getWidth(), dp(360));
            int height = Math.max(1, sampled.getHeight() * width / sampled.getWidth());
            thumb = Bitmap.createScaledBitmap(sampled, width, height, true);
            try (OutputStream output = VaultFiles.output(imageFile(id))) { output.write(bytes); }
            try (OutputStream output = VaultFiles.output(thumbFile(id))) {
                if (!thumb.compress(sampled.hasAlpha()?Bitmap.CompressFormat.PNG:Bitmap.CompressFormat.JPEG, 82, output))
                    throw new IllegalStateException(UiLanguage.t("Falha ao salvar miniatura."));
            }
        } catch (Exception e) {
            VaultFiles.delete(imageFile(id));
            VaultFiles.delete(thumbFile(id));
            throw e;
        } finally {
            if (thumb != null && thumb != sampled) thumb.recycle();
            sampled.recycle();
        }
    }

    private void renderFeed() {
        if(mainReferences!=null)mainReferences.refresh();if(versionViewer!=null)versionViewer.references.refresh();
        Collections.sort(generations, (a, b) -> Long.compare(b.createdAt, a.createdAt));
        visibleGenerations.clear();
        for (Generation item : generations) if (published(item)&&(!(selectedTab==3&&!activeProjectId.isEmpty())||familyInProject(item,activeProjectId))) visibleGenerations.add(item);
        rebuildGallerySets();
        selectedGenerations.removeIf(id -> findGeneration(id)==null);
        updateMainPrivacy();
        updateGenerationStatus();
        updateGalleryActions();
        if (versionViewer != null) versionViewer.refresh();
        hideGalleryReveal();
        if (galleryAdapter != null) galleryAdapter.notifyDataSetChanged();
        if (feedList == null) return;
        for (Runnable hide : hideReveals) hide.run();
        hideReveals.clear();
        stopFeedPlayback();feedList.removeAllViews();
        if(prompterPage!=null){if(selectedTab==2)renderPrompter();else prompterPage.removeAllViews();}if(projectsPage!=null){if(selectedTab==3)renderProjects();else projectsPage.removeAllViews();}
        Collections.sort(generations, (a, b) -> Long.compare(b.createdAt, a.createdAt));
        if(selectedTab==4)referenceStudio.renderManager.run();
        if(selectedTab!=0)return;
        if (visibleGenerations.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text(UiLanguage.t("Nada por aqui ainda"), 17, TEXT, true));
            gap(empty, 5);
            empty.addView(text(UiLanguage.t("Crie uma imagem para começar seu feed."), 13, MUTED, false));
            gap(empty, 14);
            Button start = button(UiLanguage.t("Criar imagem"), ACCENT, BG);
            empty.addView(start);
            start.setOnClickListener(v -> showTab(false));
            feedList.addView(empty);
            return;
        }
        int count = Math.min(visibleCount, visibleGenerations.size());
        for (int i = 0; i < count; i++) addGenerationCard(visibleGenerations.get(i));
        if (visibleGenerations.size() > visibleCount) {
            Button more = button(UiLanguage.t("Ver mais gerações"), FIELD, TEXT);
            feedList.addView(more);
            more.setOnClickListener(v -> { visibleCount += 20; renderFeed(); });
        }
    }

    private void addGenerationCard(Generation item) {addGenerationCard(item,feedList,false);}
    private void addFeedVideo(LinearLayout card,ImageView poster,Generation item){
        final double aspect=galleryAspect(item);
        android.widget.FrameLayout media=new android.widget.FrameLayout(this){
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                int width=MeasureSpec.getSize(widthSpec),height=Math.max(1,(int)Math.round(width/aspect));
                super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));
            }
        };
        media.setBackground(round(FIELD,24,0));media.setClipToOutline(true);
        media.addView(poster,new android.widget.FrameLayout.LayoutParams(-1,-1));
        GalleryVideoPreview preview=new GalleryVideoPreview(this,videoFile(item.id));
        preview.setContentDescription(UiLanguage.t("Abrir vídeo do Feed"));preview.setOnClickListener(v->openViewer(item));
        preview.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->syncFeedPlayback());
        media.addView(preview,new android.widget.FrameLayout.LayoutParams(-1,-1));
        feedVideoPreviews.add(preview);card.addView(media,new LinearLayout.LayoutParams(-1,-2));
    }
    private void syncFeedPlayback(){
        GalleryVideoPreview chosen=null;int best=0;
        if(mainScroll!=null&&appResumed&&galleryAutoplay&&selectedTab==0&&mainScroll.isShown()&&mainScroll.hasWindowFocus()){
            android.graphics.Rect viewport=new android.graphics.Rect(),visible=new android.graphics.Rect();
            if(mainScroll.getGlobalVisibleRect(viewport))for(GalleryVideoPreview preview:feedVideoPreviews){
                if(preview.isShown()&&preview.getGlobalVisibleRect(visible)&&visible.intersect(viewport)){
                    int shown=visible.height(),required=(int)(Math.min(preview.getHeight(),viewport.height())*.45f);
                    if(shown>=required&&shown>best){chosen=preview;best=shown;}
                }
            }
        }
        for(GalleryVideoPreview preview:feedVideoPreviews)preview.active(preview==chosen);
    }
    private void stopFeedPlayback(){for(GalleryVideoPreview preview:feedVideoPreviews)preview.active(false);feedVideoPreviews.clear();}
    private void addGenerationCard(Generation item,LinearLayout target,boolean trial) {
        LinearLayout card = column();
        card.setPadding(0, 0, 0, dp(12));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(19);
        target.addView(card, cardParams);
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout identity = column();
        identity.setPadding(dp(2), 0, 0, 0);
        identity.addView(text(item.workflowName + (item.editNumber > 0 ? " · " + versionName(item) : ""), 15, TEXT, true));
        identity.addView(text(new SimpleDateFormat("dd MMM · HH:mm", Locale.forLanguageTag(UiLanguage.tag(this)))
                .format(new Date(item.createdAt)), 11, MUTED, false));
        top.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));
        if(trial){Button like=button("",Color.TRANSPARENT,TEXT);FlowDesign.symbol(like,item.temporary?"heart":"heart-filled",item.temporary?UiLanguage.t("Salvar teste no acervo"):UiLanguage.t("Teste salvo"));like.setEnabled(item.temporary&&"done".equals(item.state));top.addView(like,new LinearLayout.LayoutParams(dp(44),dp(44)));like.setOnClickListener(v->saveTrial(item));}
        if(item.prompt!=null&&!item.prompt.trim().isEmpty()){
            Button reuse=button("",Color.TRANSPARENT,TEXT);FlowDesign.symbol(reuse,"reuse",UiLanguage.t("Reutilizar prompt desta geração"));
            top.addView(reuse,new LinearLayout.LayoutParams(dp(44),dp(44)));reuse.setOnClickListener(v->reusePrompt(item));
        }
        {
            Button actions = button("⋮", Color.TRANSPARENT, MUTED);
            actions.setTextSize(22);
            actions.setContentDescription(UiLanguage.t("Opções da geração"));
            top.addView(actions, new LinearLayout.LayoutParams(dp(42), dp(42)));
            actions.setOnClickListener(v -> showGenerationActions(actions, item));
        }
        card.addView(top);
        gap(card, 6);

        File thumb = thumbFile(item.id);
        TextView caption = text(covered(item) ? UiLanguage.t("Momento") :
                (item.prompt.isEmpty() ? UiLanguage.t("Imagem gerada") : item.prompt), 14, TEXT, false);
        caption.setMaxLines(1);
        caption.setTextColor(MUTED);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if ("done".equals(item.state) && thumb.exists()) {
            ImageView image = new RoundedMediaImage();
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackgroundColor(Color.TRANSPARENT);
            image.setClipToOutline(true);
            if (covered(item)) {
                Bitmap cover = coverBitmap(item);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                image.setImageBitmap(cover);
                card.addView(image, new LinearLayout.LayoutParams(-1, -2));
                Runnable hide = () -> {
                    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    image.setImageBitmap(cover);
                    caption.setText(UiLanguage.t("Momento"));
                    updateMainPrivacy();
                };
                hideReveals.add(hide);
                image.setOnLongClickListener(v -> {
                    if (hideAllNsfw) return true;
                    Bitmap actual = VaultFiles.bitmap(thumb.getAbsolutePath());
                    if (actual == null) return false;
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    image.setImageBitmap(actual);
                    caption.setText(item.prompt.isEmpty() ? UiLanguage.t("Imagem gerada") : item.prompt);
                    return true;
                });
                image.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_UP
                            || event.getActionMasked() == MotionEvent.ACTION_CANCEL) hide.run();
                    return false;
                });
                image.setOnClickListener(v -> openViewer(item));
            } else {
                image.setAdjustViewBounds(true);
                image.setImageBitmap(VaultFiles.bitmap(thumb.getAbsolutePath()));
                if(!trial&&"video".equals(item.mediaKind)&&videoFile(item.id).isFile())addFeedVideo(card,image,item);
                else card.addView(image, new LinearLayout.LayoutParams(-1, -2));
                image.setOnClickListener(v -> openViewer(item));
            }
            if(trial){final boolean[] held={false};android.view.GestureDetector taps=new android.view.GestureDetector(this,new android.view.GestureDetector.SimpleOnGestureListener(){
                @Override public boolean onDown(MotionEvent e){held[0]=false;return true;}
                @Override public boolean onSingleTapConfirmed(MotionEvent e){if(!held[0])openViewer(item);return true;}
                @Override public boolean onDoubleTap(MotionEvent e){saveTrial(item);return true;}
                @Override public void onLongPress(MotionEvent e){held[0]=true;if(covered(item)&&!hideAllNsfw){getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);image.setImageBitmap(galleryThumbnail(thumbFile(item.id)));}}
            });image.setOnTouchListener((v,e)->{taps.onTouchEvent(e);if((e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)&&held[0]){image.setImageBitmap(covered(item)?coverBitmap(item):galleryThumbnail(thumbFile(item.id)));updateMainPrivacy();}return true;});}
        } else {
            android.widget.FrameLayout placeholder = new android.widget.FrameLayout(this);
            placeholder.setBackground(round(FIELD, 12, 0));
            placeholder.setClipToOutline(true);
            TextView status;
            if ("error".equals(item.state)) {
                status = text(item.error, 14, MUTED, false);
            } else {
                placeholder.addView(new GenerationPulse(), new android.widget.FrameLayout.LayoutParams(-1, -1));
                status = text("running".equals(item.state) ? ("video".equals(item.mediaKind) ? UiLanguage.t("Animando sua imagem…") : UiLanguage.t("Criando sua imagem…")) : UiLanguage.t("Na fila…"), 14, TEXT, false);
            }
            status.setGravity(Gravity.CENTER);
            status.setPadding(dp(20), dp(20), dp(20), dp(20));
            placeholder.addView("error".equals(item.state)?status:new GenerationProgressView(item,false),new android.widget.FrameLayout.LayoutParams(-1,-1));
            card.addView(placeholder, new LinearLayout.LayoutParams(-1, dp("error".equals(item.state) ? 104 : 220)));
        }
        gap(card, 12);
        caption.setVisibility("error".equals(item.state) ? View.GONE : View.VISIBLE);
        card.addView(caption);
        gap(card, 4);
    }

    private boolean protectCharacterCovers(String sourceId) {
        try{referenceStore.protect(sourceId);}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();return false;}
        List<CharacterPreset> updated = new ArrayList<>();
        boolean changed = false;
        for (CharacterPreset person : characters) {
            if (sourceId.equals(person.sourceId) && !person.privateCover) {
                updated.add(new CharacterPreset(person.id, person.name, person.description, person.coverId, person.sourceId, true));
                changed = true;
            } else updated.add(person);
        }
        return !changed || saveCharacters(updated, selectedCharacterId);
    }

    private void showLipsync(Generation source,JSONObject previous){
        showLipsync(source,previous,null);
    }
    private void chooseLipsyncMedia(ReferencePreset preset){
        FlowSheet chooser=new FlowSheet(this,UiLanguage.t("Escolher mídia para Lipsync"),"lipsync");chooser.note(UiLanguage.t("Escolha uma imagem ou vídeo para usar este áudio."));int count=0;
        for(Generation item:generations)if("done".equals(item.state)&&visibleGeneration(item)){count++;chooser.action(item.workflowName+" · "+UiLanguage.t("video".equals(item.mediaKind)?"Vídeo":"Imagem"),"video".equals(item.mediaKind)?"video":"image",()->{chooser.dialog.dismiss();referenceStudio.audio.copyFragment(preset,audio->{openViewer(item);showLipsync(item,null,audio);});});}
        if(count==0)chooser.note(UiLanguage.t("Adicione uma imagem ou vídeo à galeria primeiro."));chooser.show();
    }
    private void showLipsync(Generation source,JSONObject previous,LipsyncAudio supplied){
        if(!"done".equals(source.state)||source.nsfw&&hideAllNsfw){LipsyncAudio.discard(supplied);return;}
        if(versionViewer!=null){versionViewer.conceal();versionViewer.stopVideo();}
        if(lipsyncSheet!=null)lipsyncSheet.dialog.dismiss();
        worker.execute(()->{try{
            JSONObject options=previous==null?new JSONObject():previous.optJSONObject("options");if(options==null)options=new JSONObject();
            String audioId=options.optString("audioLocalId");LipsyncAudio restored=supplied;
            if(restored==null&&GenerationStorage.valid(audioId)){File file=LipsyncAudio.file(getFilesDir(),audioId);if(file.isFile())restored=LipsyncAudio.read(file,options.optString("audioName",UiLanguage.t("Áudio selecionado")));}
            final LipsyncAudio audio=restored;final int size=options.optInt("maxSide",1024);
            runOnUiThread(()->{if(isFinishing()||isDestroyed()||findGeneration(source.id)==null){LipsyncAudio.discard(audio);return;}
                lipsyncSheet=new LipsyncSheet(this,"video".equals(source.mediaKind),audio,size,worker,()->{
                    final LipsyncSheet target=lipsyncSheet;referenceStudio.audio.choose(true,target::accept);
                },(selected,resolution)->submitLipsync(source,selected,resolution,previous));
            });
        }catch(Exception error){runOnUiThread(()->Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show());}});
    }
    private boolean submitLipsync(Generation source,LipsyncAudio audio,int maxSide,JSONObject previous){
        if(editUploads.contains(source.id))return false;
        if(!connected||baseUrl==null){Toast.makeText(this,UiLanguage.t("Conecte ao ComfyUI em Ajustes."),Toast.LENGTH_LONG).show();return false;}
        final String address=baseUrl,profile=memoryProfile();final boolean video="video".equals(source.mediaKind),hidden=source.nsfw||!hideAllNsfw;
        final VersionViewer owner=versionViewer;final Destination destination=new Destination(owner);
        editUploads.add(source.id);Toast.makeText(this,UiLanguage.t("Preparando lipsync…"),Toast.LENGTH_SHORT).show();
        worker.execute(()->{try{
            int seed=previous==null?new java.util.Random().nextInt(Integer.MAX_VALUE):previous.optJSONObject("options").optInt("seed",42);
            JSONObject template=LipsyncWorkflow.graph(video,"source","audio",maxSide,LipsyncWorkflow.batch(profile),seed);
            WorkflowDependencies.verify(template,ReferenceGraphs.schema(referenceJobs.server,address,WorkflowDependencies.types(template)));
            String media;long duration=audio.millis;
            if(video){File file=videoFile(source.id);VideoClip clip=VideoClip.read(file,false);if(duration>clip.millis+80)throw new IllegalArgumentException(UiLanguage.t("O áudio precisa ser mais curto que o vídeo."));media=uploadMediaInput(address,file,clip.mime,"video/webm".equals(clip.mime)?".webm":".mp4");}
            else media=uploadEditInput(address,imageFile(source.id));
            String sound=uploadMediaInput(address,audio.file,"application/octet-stream",".audio");
            JSONObject graph=LipsyncWorkflow.graph(video,media,sound,maxSide,LipsyncWorkflow.batch(profile),seed);
            JSONObject options=new JSONObject().put("memoryProfile",profile).put("maxSide",maxSide).put("seed",seed).put("batch",1).put("audioName",audio.name).put("audioMillis",duration).put("agent",false);
            JSONObject recipe=new JSONObject().put("version",1).put("operation",video?"video_lipsync":"image_lipsync").put("sourceId",source.id).put("workflowName","MuseTalk 1.5")
                .put("server",address).put("template",template).put("prompt",source.prompt==null?"":source.prompt).put("promptTemplate",source.promptTemplate).put("nsfw",hidden)
                .put("options",options).put("refs",new JSONArray().put(source.toJson())).put("presets",new JSONArray());
            String id=requestJson("POST",address+"/prompt",new JSONObject().put("prompt",graph).put("client_id",UUID.randomUUID().toString())).optString("prompt_id");
            if(!validId(id))throw new IllegalStateException(UiLanguage.t("ComfyUI retornou ID inválido."));
            // Recipe audio is owned by the result, so deleting a source does not remove it.
            try{LipsyncAudio.copy(audio.file,LipsyncAudio.file(getFilesDir(),id));options.put("audioLocalId",id);}catch(Exception copyError){android.util.Log.w("bwdacc","Lipsync audio could not be retained for reuse");}
            saveRecipe(id,recipe,graph,Collections.singletonList(media));
            runOnUiThread(()->{editUploads.remove(source.id);Generation item=new Generation();item.id=id;item.serverAddress=address;item.sourceId=source.id;item.mediaKind="video";item.workflowName="MuseTalk 1.5";
                if(video){item.rootId=familyId(source);item.parentId=source.id;for(Generation version:versions(item.rootId))item.editNumber=Math.max(item.editNumber,version.editNumber);item.editNumber++;}else item.rootId=id;
                item.prompt=source.prompt;item.promptTemplate=source.promptTemplate;item.nsfw=hidden;item.state="queued";item.createdAt=System.currentTimeMillis();item.settings=GenerationDetails.snapshot(graph);destination.apply(item,source);
                generations.add(0,item);saveFeed();renderFeed();if(owner!=null&&owner.dialog.isShowing()&&owner.currentId.equals(source.id)){if(video)owner.select(id);else openViewer(item);}refreshPending();
            });
        }catch(Exception error){runOnUiThread(()->{editUploads.remove(source.id);new FlowDialogs.Builder(this).setTitle(UiLanguage.t("Lipsync indisponível")).setMessage(error.getMessage()).setPositiveButton(UiLanguage.t("OK"),null).show();});}
        finally{LipsyncAudio.discard(audio);}});return true;
    }

    private void showUpscale(Generation source,JSONObject previous){
        boolean video="video".equals(source.mediaKind);
        if(!"done".equals(source.state)){Toast.makeText(this,UiLanguage.t("Aguarde a mídia terminar."),Toast.LENGTH_SHORT).show();return;}
        JSONObject options=previous==null?new JSONObject():previous.optJSONObject("options");if(options==null)options=new JSONObject();
        UpscaleSheet.show(this,video,options.optBoolean("compact",true),options.optInt("upscaleSize",video?1920:2),UpscaleWorkflow.unavailable(video,memoryProfile()),
            (compact,size)->submitUpscale(source,compact,size,previous));
    }
    private void submitUpscale(Generation source,boolean compact,int size,JSONObject previous){
        if(editUploads.contains(source.id))return;
        if(!connected||baseUrl==null){Toast.makeText(this,UiLanguage.t("Conecte ao ComfyUI em Ajustes."),Toast.LENGTH_LONG).show();return;}
        final String address=baseUrl,profile=memoryProfile();final boolean video="video".equals(source.mediaKind),hidden=source.nsfw||!hideAllNsfw;
        final VersionViewer owner=versionViewer;final Destination destination=new Destination(owner);
        editUploads.add(source.id);Toast.makeText(this,UiLanguage.t("Preparando upscale…"),Toast.LENGTH_SHORT).show();
        worker.execute(()->{try{
            String reason=UpscaleWorkflow.unavailable(video,profile);if(!reason.isEmpty())throw new IllegalArgumentException(reason);
            JSONObject template=bundledWorkflow(UpscaleWorkflow.asset(video,compact));
            if(previous!=null&&previous.getJSONObject("options").optBoolean("compact")==compact)template=GenerationRecipe.configured(previous);
            JSONObject graph=GenerationRecipe.copy(template);
            WorkflowDependencies.verify(graph,ReferenceGraphs.schema(referenceJobs.server,address,WorkflowDependencies.types(graph)));
            int width,height;long millis=0;String uploaded;
            if(video){File file=videoFile(source.id);VideoClip clip=VideoClip.read(file,false);width=clip.width;height=clip.height;millis=clip.millis;
                uploaded=uploadMediaInput(address,file,clip.mime,"video/webm".equals(clip.mime)?".webm":".mp4");}
            else{File file=imageFile(source.id);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;VaultFiles.bitmap(file.getAbsolutePath(),bounds);width=bounds.outWidth;height=bounds.outHeight;uploaded=uploadEditInput(address,file);}
            long seed=previous!=null&&graph.has("noise")?graph.getJSONObject("noise").getJSONObject("inputs").optLong("noise_seed"):new java.util.Random().nextLong();
            UpscaleWorkflow.prepare(graph,uploaded,source.prompt==null?"":source.prompt,width,height,millis,size,seed);
            MemoryProfiles.apply(graph,profile);WorkflowDependencies.verify(graph,ReferenceGraphs.schema(referenceJobs.server,address,WorkflowDependencies.types(graph)));
            JSONObject options=new JSONObject().put("memoryProfile",profile).put("compact",compact).put("upscaleSize",size).put("ratio","Original").put("batch",1).put("agent",false).put("agentNsfw",hidden);
            JSONObject recipe=new JSONObject().put("version",1).put("operation",video?"video_upscale":"image_upscale").put("sourceId",source.id).put("workflowName",UpscaleWorkflow.name(video,compact))
                .put("server",address).put("template",template).put("promptTemplate",source.promptTemplate).put("prompt",source.prompt==null?"":source.prompt).put("nsfw",hidden)
                .put("options",options).put("refs",new JSONArray().put(source.toJson())).put("presets",new JSONArray());
            String id=requestJson("POST",address+"/prompt",new JSONObject().put("prompt",graph).put("client_id",UUID.randomUUID().toString())).optString("prompt_id");
            if(!validId(id))throw new IllegalStateException(UiLanguage.t("ComfyUI retornou ID inválido."));saveRecipe(id,recipe,graph,Collections.singletonList(uploaded));
            final JSONObject submitted=graph;runOnUiThread(()->{editUploads.remove(source.id);Generation item=new Generation();item.id=id;item.serverAddress=address;item.rootId=familyId(source);item.parentId=source.id;item.sourceId=source.id;
                for(Generation version:versions(item.rootId))item.editNumber=Math.max(item.editNumber,version.editNumber);item.editNumber++;item.mediaKind=source.mediaKind;item.workflowName=source.workflowName;
                item.prompt=source.prompt;item.promptTemplate=source.promptTemplate;item.nsfw=hidden;item.state="queued";item.createdAt=System.currentTimeMillis();item.settings=GenerationDetails.snapshot(submitted);destination.apply(item,source);
                generations.add(0,item);saveFeed();renderFeed();if(owner!=null&&owner.dialog.isShowing()&&owner.currentId.equals(source.id))owner.select(id);refreshPending();
            });
        }catch(Exception e){runOnUiThread(()->{editUploads.remove(source.id);new FlowDialogs.Builder(this).setTitle(UiLanguage.t("Upscale indisponível")).setMessage(e.getMessage()).setPositiveButton(UiLanguage.t("OK"),null).show();});}});
    }

    private void showImageTool(Generation source,boolean expand,JSONObject previous){
        if(!"image".equals(source.mediaKind)||!"done".equals(source.state)||(source.nsfw&&hideAllNsfw))return;
        final boolean stealth=source.nsfw&&privateCoversEnabled;
        worker.execute(()->{try{
            File file=imageFile(source.id);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;VaultFiles.bitmap(file.getAbsolutePath(),bounds);
            int orientation=imageOrientation(VaultFiles.open(file));final int width=orientation>=5?bounds.outHeight:bounds.outWidth,height=orientation>=5?bounds.outWidth:bounds.outHeight;
            if(width<1||height<1)throw new IllegalArgumentException(UiLanguage.t("Dimensões da mídia indisponíveis."));
            final Bitmap preview=stealth?coverBitmap(source):viewerBitmap(file);
            runOnUiThread(()->{if(isFinishing()||isDestroyed()||findGeneration(source.id)==null||(source.nsfw&&hideAllNsfw)){if(preview!=null)preview.recycle();return;}
                if(stealth!=(source.nsfw&&privateCoversEnabled)){if(preview!=null)preview.recycle();showImageTool(source,expand,previous);return;}
                JSONObject options=previous==null?null:previous.optJSONObject("options");
                if(expand){OutpaintSheet.show(this,preview,width,height,options==null?null:options.optJSONObject("edges"),previous==null?"":previous.optString("promptTemplate"),(edges,prompt)->submitImageTool(source,true,edges,prompt,previous));return;}
                FlowSheet sheet=new FlowSheet(this,UiLanguage.t("Remover fundo"),"cutout");ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageBitmap(preview);sheet.body.addView(image,new LinearLayout.LayoutParams(-1,dp(180)));sheet.gap(sheet.body,12);sheet.note(UiLanguage.t("BiRefNet · PNG transparente. Cria uma versão e mantém a original."));sheet.action(UiLanguage.t("Remover fundo"),"cutout",()->{sheet.dialog.dismiss();submitImageTool(source,false,new JSONObject(),"",previous);});sheet.dialog.setOnDismissListener(d->{if(preview!=null&&!preview.isRecycled())preview.recycle();});sheet.show();
            });
        }catch(Exception error){runOnUiThread(()->Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show());}});
    }
    private void submitImageTool(Generation source,boolean expand,JSONObject edges,String prompt,JSONObject previous){
        if(editUploads.contains(source.id)||(source.nsfw&&hideAllNsfw))return;
        if(!connected||baseUrl==null){Toast.makeText(this,UiLanguage.t("Conecte ao ComfyUI em Ajustes."),Toast.LENGTH_LONG).show();return;}
        final String address=baseUrl,profile=memoryProfile();final boolean hidden=source.nsfw||!hideAllNsfw;
        final VersionViewer owner=versionViewer;final Destination destination=new Destination(owner);
        final boolean snofs=!hideAllNsfw&&(owner==null||getPreferences(0).getBoolean("snofs_"+owner.draftKey(),true));
        editUploads.add(source.id);Toast.makeText(this,UiLanguage.t("Preparando edição…"),Toast.LENGTH_SHORT).show();
        worker.execute(()->{try{
            File file=imageFile(source.id);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;VaultFiles.bitmap(file.getAbsolutePath(),bounds);int orientation=imageOrientation(VaultFiles.open(file));int width=orientation>=5?bounds.outHeight:bounds.outWidth,height=orientation>=5?bounds.outWidth:bounds.outHeight;
            int[] output=expand?ImageToolsWorkflow.dimensions(width,height,edges):new int[]{width,height};
            JSONObject old=previous==null?new JSONObject():previous.optJSONObject("options");if(old==null)old=new JSONObject();long seed=old.has("seed")?old.optLong("seed"):new java.util.Random().nextLong()&Long.MAX_VALUE;
            boolean selectedSnofs=old.has("snofs")?old.optBoolean("snofs")&&!hideAllNsfw:snofs;
            JSONObject graph=expand?ImageToolsWorkflow.outpaint(bundledWorkflow(ImageEditWorkflow.asset(selectedSnofs)),"source.png",width,height,edges,prompt,seed):ImageToolsWorkflow.removeBackground("source.png");
            if(expand)MemoryProfiles.apply(graph,profile);
            WorkflowDependencies.verify(graph,ReferenceGraphs.schema(referenceJobs.server,address,WorkflowDependencies.types(graph)));
            JSONObject template=GenerationRecipe.copy(graph);String uploaded=uploadEditInput(address,file);
            graph.getJSONObject(expand?"1":"source").getJSONObject("inputs").put("image",uploaded);
            String name=expand?ImageEditWorkflow.name(graph)+" · Outpainting":"BiRefNet";
            JSONObject options=new JSONObject().put("memoryProfile",profile).put("edges",edges).put("seed",seed).put("snofs",selectedSnofs).put("batch",1).put("agent",false);
            JSONObject recipe=new JSONObject().put("version",1).put("operation",expand?ImageToolsWorkflow.EXPAND:ImageToolsWorkflow.REMOVE).put("sourceId",source.id).put("workflowName",name).put("server",address).put("template",template)
                .put("promptTemplate",prompt).put("prompt",expand?prompt:source.prompt).put("nsfw",hidden).put("options",options).put("refs",new JSONArray().put(source.toJson())).put("presets",new JSONArray());
            String id=requestJson("POST",address+"/prompt",new JSONObject().put("prompt",graph).put("client_id",UUID.randomUUID().toString())).optString("prompt_id");
            if(!validId(id))throw new IllegalStateException(UiLanguage.t("ComfyUI retornou ID inválido."));saveRecipe(id,recipe,graph,Collections.singletonList(uploaded));
            runOnUiThread(()->{editUploads.remove(source.id);Generation item=new Generation();item.id=id;item.serverAddress=address;item.rootId=familyId(source);item.parentId=source.id;item.sourceId=source.id;item.mediaKind="image";
                for(Generation version:versions(item.rootId))item.editNumber=Math.max(item.editNumber,version.editNumber);item.editNumber++;item.workflowName=source.workflowName;item.prompt=expand?prompt:source.prompt;item.promptTemplate=expand?prompt:source.promptTemplate;item.nsfw=hidden;item.state="queued";item.createdAt=System.currentTimeMillis();item.mediaWidth=output[0];item.mediaHeight=output[1];item.settings=GenerationDetails.snapshot(graph);destination.apply(item,source);
                generations.add(0,item);saveFeed();renderFeed();if(owner!=null&&owner.dialog.isShowing()&&owner.currentId.equals(source.id))owner.select(id);refreshPending();
            });
        }catch(Exception error){runOnUiThread(()->{editUploads.remove(source.id);new FlowDialogs.Builder(this).setTitle(expand?UiLanguage.t("Expandir imagem"):UiLanguage.t("Remover fundo")).setMessage(error.getMessage()).setPositiveButton(UiLanguage.t("OK"),null).show();});}});
    }

    private void addExtractionActions(PopupMenu menu,Generation item){
        if(!"image".equals(item.mediaKind)||!"done".equals(item.state)||!imageFile(item.id).exists())return;
        menu.getMenu().add(UiLanguage.t("Selecionar objeto")).setOnMenuItemClickListener(choice->{extractFromGeneration(item,false);return true;});
        menu.getMenu().add(UiLanguage.t("Extrair composição")).setOnMenuItemClickListener(choice->{extractFromGeneration(item,true);return true;});
    }
    private void extractFromGeneration(Generation item,boolean composition){
        if(item.nsfw&&hideAllNsfw)return;
        hideGalleryReveal();for(Runnable hide:hideReveals)hide.run();if(versionViewer!=null){versionViewer.conceal();versionViewer.stopVideo();}
        worker.execute(()->{try(InputStream input=VaultFiles.open(imageFile(item.id))){ReferencePreset.Picture copy=saveReferenceImage(input,item.id,item.nsfw);runOnUiThread(()->referenceStudio.extractor.open(copy,composition));}
            catch(Exception error){runOnUiThread(()->Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void showGenerationActions(View anchor,Generation item){showLibraryActions(anchor,item,false);}
    private void markPrivate(Generation item){
        if(hideAllNsfw)return;boolean previous=item.nsfw;if(!previous&&!protectCharacterCovers(item.id))return;item.nsfw=!previous;if(!saveFeed())item.nsfw=previous;renderFeed();
    }
    private void verifyResult(Generation item){
        if(baseUrl==null||!item.serverAddress.isEmpty()&&!item.serverAddress.equals(baseUrl)){Toast.makeText(this,UiLanguage.t("Conecte ao servidor original desta geração."),Toast.LENGTH_LONG).show();return;}
        final String key=baseUrl+"|"+item.id;worker.execute(()->{missingJobs.remove(key);downloadFailures.remove(key);});item.state="queued";item.error="";saveFeed();renderFeed();refreshPending();
    }
    private void showLibraryActions(View anchor,Generation item,boolean wholeSet){
        hideGalleryReveal();for(Runnable hide:hideReveals)hide.run();PopupMenu menu=new OrganicPopupMenu(this,anchor);
        menu.getMenu().add(UiLanguage.t("Reutilizar prompt")).setOnMenuItemClickListener(m->{reusePrompt(item);return true;});
        menu.getMenu().add(UiLanguage.t("Editar")).setOnMenuItemClickListener(m->{openViewer(item);return true;});
        if(!"video".equals(item.mediaKind)&&"done".equals(item.state)&&imageFile(item.id).exists())menu.getMenu().add(UiLanguage.t("Animar imagem")).setOnMenuItemClickListener(m->{openViewer(item);versionViewer.openEditor(true);return true;});
        menu.getMenu().add(UiLanguage.t("Adicionar a projeto")).setOnMenuItemClickListener(m->{chooseProjectFor(item);return true;});
        if(!hideAllNsfw)menu.getMenu().add(item.nsfw?UiLanguage.t("Remover marcação NSFW"):UiLanguage.t("Marcar como NSFW")).setOnMenuItemClickListener(m->{markPrivate(item);return true;});
        menu.getMenu().add(UiLanguage.t("Apagar")).setEnabled(canDelete(item)).setOnMenuItemClickListener(m->{if(wholeSet)confirmDeleteSets(java.util.Collections.singleton(item.id));else confirmDelete(item);return true;});showIconMenu(menu);
    }

    private boolean restoringRecipe;
    private void reusePrompt(Generation item) {
        if(mainFold!=null)mainFold.setCollapsed(false);
        if(restoringRecipe)return;
        if(item.prompt==null||item.prompt.trim().isEmpty()){boolean upscale=false;try{String operation=GenerationRecipe.load(getFilesDir(),item.id).optString("operation");upscale=operation.endsWith("_upscale")||operation.endsWith("_lipsync")||ImageToolsWorkflow.operation(operation);}catch(Exception ignored){}if(!upscale){Toast.makeText(this,UiLanguage.t("Esta mídia não tem prompt salvo."),Toast.LENGTH_LONG).show();return;}}
        if(item.nsfw&&hideAllNsfw){Toast.makeText(this,UiLanguage.t("Ative a visualização NSFW para reutilizar esta geração."),Toast.LENGTH_LONG).show();return;}
        restoringRecipe=true;Toast.makeText(this,UiLanguage.t("Restaurando geração…"),Toast.LENGTH_SHORT).show();
        final String address=item.serverAddress.isEmpty()?baseUrl:item.serverAddress;
        final VersionViewer origin=versionViewer;final String originId=origin==null?"":origin.currentId;
        worker.execute(()->{
            try{
                JSONObject saved;try{saved=GenerationRecipe.load(getFilesDir(),item.id);}catch(Exception missing){
                    if(address==null||address.isEmpty())throw new IllegalStateException(UiLanguage.t("Configuração antiga indisponível. Conecte ao servidor original."));
                    JSONObject job=getJson(address+"/history/"+item.id).optJSONObject(item.id);
                    if(job==null||job.optJSONArray("prompt")==null||job.getJSONArray("prompt").optJSONObject(2)==null)throw new IllegalStateException(UiLanguage.t("O servidor não tem mais a configuração desta geração. Não é possível recuperar os ajustes originais."));
                    saved=legacyRecipe(item,job.getJSONArray("prompt").getJSONObject(2),address);
                }
                final JSONObject recipe=saved;List<Generation> recovered=restoreRecipeInputs(recipe);
                GenerationRecipe.save(getFilesDir(),item.id,recipe);
                runOnUiThread(()->{
                    restoringRecipe=false;
                    // A slow download must not pull the user out of another image or a closed editor.
                    if(origin!=null&&(!origin.dialog.isShowing()||versionViewer!=origin||!origin.currentId.equals(originId)))return;
                    try{for(Generation input:recovered)restoredInputs.put(input.id,input);saveRestoredInputs();recipeCache.put(item.id,recipe);restoreRecipe(item,recipe);}
                    catch(Exception e){Toast.makeText(this,UiLanguage.t("Não foi possível reutilizar: ")+e.getMessage(),Toast.LENGTH_LONG).show();}
                });
            }catch(Exception e){runOnUiThread(()->{restoringRecipe=false;Toast.makeText(this,UiLanguage.t("Não foi possível reutilizar: ")+e.getMessage(),Toast.LENGTH_LONG).show();});}
        });
    }

    private JSONObject legacyRecipe(Generation item,JSONObject graph,String address)throws Exception{
        boolean video="video".equals(item.mediaKind),edit=!item.parentId.isEmpty();
        if(video&&!edit&&!VideoWorkflow.matches(graph))throw new IllegalStateException(UiLanguage.t("Este workflow antigo não tem uma configuração reutilizável salva. As novas gerações passam a guardar tudo localmente."));
        boolean extending=graph.has("momentos_extend_source");JSONObject options=GenerationRecipe.legacyOptions(graph,video);String name=extending?VideoExtendWorkflow.name(options.optBoolean("turbo")):edit?(video?VideoEditWorkflow.name(options.optBoolean("turbo")):"Klein · Referências"):item.workflowName;
        List<String> inputs=GenerationRecipe.inputFiles(graph,video&&edit);JSONArray refs=new JSONArray();
        String source=edit?item.parentId:video?item.sourceId:"";
        for(int i=0;i<inputs.size();i++){
            Generation known=i==0&&!source.isEmpty()?findGeneration(source):null;
            JSONObject ref=known==null?new JSONObject().put("id",i==0&&!source.isEmpty()?source:UUID.nameUUIDFromBytes((address+"|"+inputs.get(i)).getBytes(StandardCharsets.UTF_8)).toString()).put("workflowName","Referência original").put("state","done").put("nsfw",item.nsfw).put("mediaKind",video&&edit&&i==0?"video":"image"):known.toJson();
            ref.put("input",inputs.get(i));refs.put(ref);
        }
        // Expanded text is the only immutable description available in old history.
        JSONObject recipe=new JSONObject().put("version",1).put("legacy",true).put("operation",extending?"video_extend":video?(edit?"video_edit":"video"):(edit?"image_edit":"image"))
            .put("workflowName",name).put("sourceId",source).put("server",address).put("template",GenerationRecipe.legacyTemplate(graph)).put("graph",GenerationRecipe.copy(graph))
            .put("promptTemplate",item.prompt).put("prompt",item.prompt).put("nsfw",item.nsfw).put("options",options).put("refs",refs).put("presets",new JSONArray());
        JSONObject template=recipe.getJSONObject("template");for(Iterator<String> ids=template.keys();ids.hasNext();){String id=ids.next();JSONObject values=template.getJSONObject(id).getJSONObject("inputs");for(Iterator<String> keys=values.keys();keys.hasNext();){String key=keys.next();if(item.prompt.equals(values.opt(key)))recipe.put("promptNode",id).put("promptKey",key);}}
        return recipe;
    }

    private List<Generation> restoreRecipeInputs(JSONObject recipe)throws Exception{
        List<Generation> recovered=new ArrayList<>();JSONArray refs=recipe.getJSONArray("refs");String address=recipe.getString("server");
        for(int i=0;i<refs.length();i++){
            if(generationStorage.deleted(refs.getJSONObject(i).optString("id")))throw new IllegalStateException(UiLanguage.t("Uma referência original foi apagada da Galeria. Ela não será restaurada automaticamente."));
            JSONObject data=refs.getJSONObject(i);Generation existing=findGeneration(data.getString("id"));Generation ref=existing==null?Generation.fromJson(data):existing;
            boolean video="video".equals(ref.mediaKind);File local=video?videoFile(ref.id):imageFile(ref.id);
            if(!local.isFile()){
                String input=data.optString("input");JSONObject path;
                if(!input.isEmpty()){String type="input";if(input.endsWith(" [input]")||input.endsWith(" [output]")){type=input.endsWith(" [output]")?"output":"input";input=input.substring(0,input.lastIndexOf(" ["));}input=input.replace('\\','/');int split=input.lastIndexOf('/');path=new JSONObject().put("filename",input.substring(split+1)).put("subfolder",split<0?"":input.substring(0,split)).put("type",type);}
                else if(ref.filename!=null&&!ref.filename.isEmpty())path=new JSONObject().put("filename",ref.filename).put("subfolder",ref.subfolder).put("type",ref.imageType);
                else throw new IllegalStateException(UiLanguage.t("Uma referência original foi apagada e não pode ser recuperada."));
                if(video)saveVideo(address+imagePath(path),ref.id);else saveImage(ref.id,requestBytes("GET",address+imagePath(path),null,30_000_000));
            }
            if(existing==null){ref.state="done";recovered.add(ref);}
        }
        if(GenerationRecipe.edit(recipe)&&!GenerationRecipe.referenceIds(recipe).contains(GenerationRecipe.source(recipe)))throw new IllegalStateException(UiLanguage.t("Referência base ausente desta edição."));
        return recovered;
    }
    private void saveRestoredInputs()throws Exception{
        JSONArray entries=new JSONArray();for(Generation ref:restoredInputs.values())entries.put(ref.toJson());writeWorkflow(new File(getFilesDir(),"reuse-inputs.json"),new JSONObject().put("items",entries));
    }
    private void loadRestoredInputs(){
        if(!new File(getFilesDir(),"reuse-inputs.json").exists()&&!new File(getFilesDir(),"reuse-inputs.json.bak").exists())return;
        try(InputStream in=VaultFiles.open(new File(getFilesDir(),"reuse-inputs.json"))){JSONArray entries=new JSONObject(new String(read(in,2_000_000),StandardCharsets.UTF_8)).getJSONArray("items");for(int i=0;i<entries.length();i++){Generation ref=Generation.fromJson(entries.getJSONObject(i));if(validId(ref.id)&&!generationStorage.deleted(ref.id))restoredInputs.put(ref.id,ref);}}catch(Exception e){throw new IllegalStateException(UiLanguage.t("Referências indisponíveis. Dados preservados."),e);}
    }
    private void restoreRecipe(Generation item,JSONObject recipe)throws Exception{
        JSONObject options=recipe.getJSONObject("options"),configured=GenerationRecipe.configured(recipe),graph=recipe.getJSONObject("graph");
        if(options.has("memoryProfile")){getPreferences(0).edit().putString("memory_profile",MemoryProfiles.normalize(options.optString("memoryProfile"))).apply();buildMemorySettings();}
        List<String> references=GenerationRecipe.referenceIds(recipe);String address=recipe.getString("server");
        String operation=recipe.optString("operation");int capacity=GenerationRecipe.edit(recipe)?3:operation.equals("video")?(options.optBoolean("firstLast")?2:1):ReferenceSupport.capacity(configured);
        if(references.size()>capacity)throw new IllegalStateException(UiLanguage.t("O histórico antigo contém mais referências do que este editor suporta. Nenhuma referência será descartada silenciosamente."));
        boolean privateResult=item.nsfw||recipe.optBoolean("nsfw");for(String id:references){Generation ref=findGeneration(id);if(ref==null)throw new IllegalStateException(UiLanguage.t("Referência indisponível."));privateResult|=ref.nsfw;}
        if(privateResult&&!owlControlsUnlocked)throw new IllegalStateException(UiLanguage.t("Libere os controles na versão do app antes de reutilizar esta geração."));
        if(hideAllNsfw==privateResult)toggleNsfwVisibility();
        if(!address.equals(baseUrl)||!connected){serverInput.setText(address);connected=false;connect();}
        if(operation.endsWith("_lipsync")){Generation source=findGeneration(GenerationRecipe.source(recipe));if(source==null)throw new IllegalStateException(UiLanguage.t("Mídia original indisponível."));if(versionViewer==null)openViewer(source);else versionViewer.select(source.id);showLipsync(source,recipe);return;}
        if(operation.endsWith("_upscale")){Generation source=findGeneration(GenerationRecipe.source(recipe));if(source==null)throw new IllegalStateException(UiLanguage.t("Mídia original indisponível."));if(versionViewer==null)openViewer(source);else versionViewer.select(source.id);showUpscale(source,recipe);return;}
        if(ImageToolsWorkflow.operation(operation)){Generation source=findGeneration(GenerationRecipe.source(recipe));if(source==null)throw new IllegalStateException(UiLanguage.t("Mídia original indisponível."));if(versionViewer==null)openViewer(source);else versionViewer.select(source.id);showImageTool(source,ImageToolsWorkflow.EXPAND.equals(operation),recipe);return;}
        String raw=recipe.optString("promptTemplate",recipe.optString("prompt"));
        boolean editor=GenerationRecipe.edit(recipe)||recipe.optString("operation").equals("video")&&!GenerationRecipe.source(recipe).isEmpty();
        VersionViewer owner=null;
        if(editor){
            Generation source=findGeneration(GenerationRecipe.source(recipe));if(source==null)throw new IllegalStateException(UiLanguage.t("Mídia de origem indisponível."));
            owner=versionViewer;
            if(owner==null){openViewer(source);owner=versionViewer;}else owner.select(source.id);
            if(owner==null)throw new IllegalStateException(UiLanguage.t("Não foi possível abrir a mídia original."));
            owner.animationEditor=recipe.optString("operation").startsWith("video");owner.editTurbo=options.optBoolean("turbo");owner.turbo=options.optBoolean("turbo");
            owner.extending=operation.equals("video_extend");owner.extensionRange=options.optJSONObject("videoRange")==null?new JSONObject():GenerationRecipe.copy(options.getJSONObject("videoRange"));
            owner.batch=GenerationOptions.count(options.optInt("batch",1));owner.ratio=options.optString("ratio","Original");owner.videoRatio=owner.ratio;owner.clipRatio=owner.ratio;
            owner.imageMegapixels=options.optInt("imageMp",1);owner.videoQuality=options.optInt("quality",33);owner.durationIndex=Math.max(0,Math.min(3,options.optInt("duration",1)));owner.editDurationIndex=options.optInt("duration",-1);owner.firstLastFrame=options.optBoolean("firstLast");
            getPreferences(0).edit().putString(reuseKey(owner),item.id).apply();
            getPreferences(0).edit().putString("options_"+reuseKey(owner),options.toString()).apply();
            getPreferences(0).edit().putString(advancedKey(editTemplate(owner),viewerAdvancedName(owner)),GenerationRecipe.advanced(graph).toString()).apply();
            getPreferences(0).edit().putBoolean(agentKey(owner),PromptAgent.requested(recipe)).apply();
            owner.bindDraft();owner.references.replace(references);owner.instruction.setText(raw);owner.instruction.setSelection(owner.instruction.length());owner.saveEditDraft();UiMotion.hide(owner.optionPanel);owner.refresh();owner.instruction.requestFocus();
        }else{
            if(versionViewer!=null)versionViewer.dialog.dismiss();
            String name=recipe.getString("workflowName");batchCount=GenerationOptions.count(options.optInt("batch",1));videoFirstLast=options.optBoolean("firstLast");videoRatio=options.optString("ratio","16:9");videoQuality=options.optInt("quality",33);videoDuration=Math.max(0,Math.min(3,options.optInt("duration",1)));
            getPreferences(0).edit().putInt("batch_count",batchCount).putString("ratio_"+name,options.optString("ratio","Original")).putInt("image_mp_"+name,options.optInt("imageMp",1))
                .putBoolean("main_video_first_last",videoFirstLast).putString("main_video_ratio",videoRatio).putInt("main_video_quality",videoQuality).putInt("main_video_duration",videoDuration).apply();
            activateWorkflow(configured,name);getPreferences(0).edit().putString(reuseKey(null),item.id).putString(advancedKey(workflow,name),GenerationRecipe.advanced(graph).toString())
                .putString(loraKey(),LoraSupport.defaults(configured,LoraSupport.loaders(configured)).toString()).apply();
            getPreferences(0).edit().putBoolean(agentKey(null),PromptAgent.requested(recipe)).apply();
            if(options.has("layout"))getPreferences(0).edit().putString("layout_"+mainDraftKey(),options.getJSONObject("layout").toString()).apply();
            mainReferences.replace(references);dockPrompt.setText(raw);dockPrompt.setSelection(dockPrompt.length());saveDraft();UiMotion.hide(quickOptions);updateComposerChip();updateLoraButton();refreshAdvancedSettings();if(settingsDialog!=null)settingsDialog.dismiss();renderFeed();if(mainFold!=null)mainFold.setCollapsed(false);dockPrompt.requestFocus();
        }
        result.setText(UiLanguage.t("Geração restaurada. Revise e continue de onde parou."));
        if(recipe.optBoolean("legacy"))Toast.makeText(this,UiLanguage.t("Ajustes recuperados do servidor. O lote antigo não era salvo; usando x1."),Toast.LENGTH_LONG).show();
    }

    private void confirmDelete(Generation item) {
        if (!canDelete(item)) {
            Toast.makeText(this, UiLanguage.t("Aguarde o envio ou a geração terminar."), Toast.LENGTH_LONG).show(); return;
        }
        new FlowDialogs.Builder(this)
                .setTitle(item.editNumber > 0 ? UiLanguage.t("Apagar este edit?") : UiLanguage.t("Apagar geração?"))
                .setMessage(UiLanguage.t("Remove do feed e apaga os arquivos deste app. ")
                        + UiLanguage.t("Outras versões e arquivos do ComfyUI permanecem."))
                .setNegativeButton(UiLanguage.t("Cancelar"), null)
                .setPositiveButton(UiLanguage.t("Apagar"), (dialog, which) -> deleteGeneration(item))
                .show();
    }

    private boolean applyGenerationDetails(Generation item,JSONObject job){
        boolean changed=false;long[] times=GenerationDetails.executionTimes(job);
        if(times[0]>0&&item.generationStartedAt!=times[0]){item.generationStartedAt=times[0];changed=true;}
        if(times[1]>0&&item.generationFinishedAt!=times[1]){item.generationFinishedAt=times[1];changed=true;}
        if(item.settings.length()==0){JSONObject recovered=GenerationDetails.historySettings(job);if(recovered.length()>0){item.settings=recovered;changed=true;}}
        String rewritten=PromptAgent.output(job);if(!rewritten.isEmpty()&&!rewritten.equals(item.settings.optString("Prompt do Agente"))){try{item.settings.put("Prompt do Agente",rewritten);changed=true;}catch(Exception ignored){}}
        return changed;
    }
    private void detailRow(LinearLayout list,String label,String value){label=UiLanguage.t(label);
        if(value==null||value.trim().isEmpty())return;
        LinearLayout group=column();group.setPadding(dp(6),dp(10),dp(6),dp(10));
        group.addView(text(label,12,MUTED,false));TextView content=text(value,14,TEXT,false);content.setTextIsSelectable(true);group.addView(content);list.addView(group);
    }
    private void openGenerationInfo(Generation item){
        if(!visibleGeneration(item))return;
        if(versionViewer!=null){versionViewer.cancelMotion();versionViewer.conceal();versionViewer.video.freezeForGesture();}
        LinearLayout sheet=column();sheet.setPadding(dp(20),dp(16),dp(20),dp(20));
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);header.addView(text(UiLanguage.t("Informações da geração"),19,TEXT,true),new LinearLayout.LayoutParams(0,dp(48),1));
        Button close=button("",Color.TRANSPARENT,TEXT);FlowDesign.symbol(close,"close",UiLanguage.t("Fechar informações"));header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));sheet.addView(header);
        ScrollView scroll=new ScrollView(this);LinearLayout details=column();scroll.addView(details);sheet.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView note=text("",12,MUTED,false);sheet.addView(note);
        Dialog info=styleSheet(sheet);if(item.nsfw)info.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        close.setOnClickListener(v->info.dismiss());info.setOnDismissListener(d->{if(versionViewer!=null&&versionViewer.dialog.isShowing())versionViewer.refresh();});
        Runnable render=()->{
            details.removeAllViews();galleryAspect(item);
            detailRow(details,UiLanguage.t("Workflow"),item.workflowName);detailRow(details,UiLanguage.t("Versão"),versionName(item));
            detailRow(details,UiLanguage.t("Criada em"),new SimpleDateFormat("dd/MM/yyyy · HH:mm:ss",Locale.forLanguageTag(UiLanguage.tag(this))).format(new Date(item.createdAt)));
            detailRow(details,UiLanguage.t("Estado"),"done".equals(item.state)?UiLanguage.t("Concluída"):"error".equals(item.state)?UiLanguage.t("Falhou"):"running".equals(item.state)?UiLanguage.t("Gerando"):UiLanguage.t("Na fila"));
            detailRow(details,UiLanguage.t("Tempo de geração"),GenerationDetails.duration(item.generationStartedAt,item.generationFinishedAt));
            if(item.originalSizeKnown)detailRow(details,UiLanguage.t("Resolução"),item.mediaWidth+" × "+item.mediaHeight+String.format(Locale.ROOT,UiLanguage.t(" · %.2f MP"),item.mediaWidth*(double)item.mediaHeight/1_000_000));
            if(item.videoMillis>0)detailRow(details,UiLanguage.t("Duração do vídeo"),String.format(Locale.ROOT,UiLanguage.t("%.2f s"),item.videoMillis/1000.0));
            Iterator<String> keys=item.settings.keys();while(keys.hasNext()){String key=keys.next();detailRow(details,key,item.settings.optString(key));}
            detailRow(details,UiLanguage.t("Prompt"),item.prompt);detailRow(details,UiLanguage.t("Erro"),item.error);
        };render.run();
        if(!item.originalSizeKnown&&"done".equals(item.state))worker.execute(()->{
            try{long[] dimensions=originalMediaInfo(item);runOnUiThread(()->{
                if(findGeneration(item.id)!=item)return;
                if(dimensions[0]>0&&dimensions[1]>0){item.mediaWidth=(int)dimensions[0];item.mediaHeight=(int)dimensions[1];item.videoMillis=dimensions[2];item.originalSizeKnown=true;saveFeed();if(info.isShowing())render.run();}
            });}catch(Exception ignored){}
        });
        String address=item.serverAddress.isEmpty()?baseUrl:item.serverAddress;
        if(address!=null&&!address.isEmpty()&&(item.settings.length()==0||item.generationFinishedAt==0)){
            note.setText(UiLanguage.t("Buscando dados no servidor…"));worker.execute(()->{
                try{JSONObject history=getJson(address+"/history/"+URLEncoder.encode(item.id,"UTF-8"));JSONObject job=history.optJSONObject(item.id);
                    runOnUiThread(()->{Generation current=findGeneration(item.id);if(current!=item)return;if(job!=null&&applyGenerationDetails(item,job))saveFeed();
                        if(info.isShowing()){render.run();note.setText(job==null?UiLanguage.t("Dados antigos indisponíveis no servidor."):"");}});
                }catch(Exception e){runOnUiThread(()->{if(info.isShowing())note.setText(UiLanguage.t("Mostrando dados salvos neste aparelho."));});}
            });
        }
    }
    private long[] originalMediaInfo(Generation item)throws Exception{
        if("video".equals(item.mediaKind)){
            android.media.MediaMetadataRetriever reader=new android.media.MediaMetadataRetriever();
            android.media.MediaDataSource vaultSource=null;
            try{vaultSource=VaultFiles.media(videoFile(item.id));reader.setDataSource(vaultSource);
                int width=Integer.parseInt(reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                int height=Integer.parseInt(reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                String rotate=reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);int rotation=rotate==null?0:Integer.parseInt(rotate);
                long duration=Long.parseLong(reader.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION));
                return rotation==90||rotation==270?new long[]{height,width,duration}:new long[]{width,height,duration};
            }finally{reader.release();VaultFiles.close(vaultSource);}
        }
        File file=imageFile(item.id);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;VaultFiles.bitmap(file.getAbsolutePath(),bounds);
        int orientation;try(InputStream input=VaultFiles.open(file)){orientation=imageOrientation(input);}
        return orientation>=5&&orientation<=8?new long[]{bounds.outHeight,bounds.outWidth,0}:new long[]{bounds.outWidth,bounds.outHeight,0};
    }

    private void deleteGeneration(Generation item) {
        java.util.Set<String> ids = new java.util.HashSet<>(); ids.add(item.id); deleteGenerations(ids);
    }

    private void deleteGenerations(java.util.Set<String> ids) {
        runStorageAction(ids,false,false,false);
    }

    private void confirmClearPrompter(){
        new FlowDialogs.Builder(this).setTitle(UiLanguage.t("Limpar feed do Prompter?"))
            .setMessage(UiLanguage.t("Apaga os testes sem like, inclusive ocultos e de sessões anteriores. Os testes salvos saem do Prompter e continuam no Feed, Galeria e Projetos. Seu prompt permanece. Tarefas já enviadas podem terminar no ComfyUI, mas não voltarão ao app."))
            .setNegativeButton(UiLanguage.t("Cancelar"),null).setPositiveButton(UiLanguage.t("Limpar"),(d,w)->runStorageAction(java.util.Collections.emptySet(),false,true,false)).show();
    }
    private void confirmSanitize(){
        new FlowDialogs.Builder(this).setTitle(UiLanguage.t("Sanitize: zerar todas as gerações?"))
            .setMessage(UiLanguage.t("Apaga todas as imagens, vídeos, testes, históricos e referências de geração deste app, inclusive itens ocultos e em andamento. Feed, Galeria e Prompter ficam vazios; projetos perdem suas gerações.\n\nWorkflows, personagens, estilos, locais, configurações e arquivos do ComfyUI permanecem. Tarefas do servidor não são canceladas e não voltarão ao app. Não é possível desfazer."))
            .setNegativeButton(UiLanguage.t("Cancelar"),null).setPositiveButton(UiLanguage.t("Apagar todas"),(d,w)->runStorageAction(java.util.Collections.emptySet(),false,false,true)).show();
    }
    private void runStorageAction(java.util.Set<String> selected,boolean wholeSets,boolean clearTrials,boolean sanitize){
        if(storageBusy)return;storageBusy=true;
        final java.util.Set<String> requested=new java.util.HashSet<>(selected);
        AlertDialog busy=new FlowDialogs.Builder(this).setTitle(sanitize?UiLanguage.t("Limpando armazenamento…"):UiLanguage.t("Limpando gerações…"))
            .setMessage(UiLanguage.t("Finalizando operações locais e removendo arquivos.")).setCancelable(false).show();
        // Wait for imports, recipe restoration and submissions already in progress. Their UI
        // callbacks enter the main queue before this callback, so cleanup includes their results.
        worker.execute(()->runOnUiThread(()->{
            final java.util.Set<String> ids=wholeSets?GenerationStorage.familyIds(generations,requested):new java.util.HashSet<>(requested);
            if(sanitize){for(Generation item:generations)ids.add(item.id);ids.addAll(restoredInputs.keySet());}
            else if(clearTrials){for(Generation item:generations)if(item.temporary)ids.add(item.id);}
            if(!sanitize&&!clearTrials)for(Generation item:generations)if(ids.contains(item.id)&&!canDelete(item)){
                storageBusy=false;busy.dismiss();Toast.makeText(this,UiLanguage.t("O conjunto mudou e ainda tem gerações em andamento. Nada apagado."),Toast.LENGTH_LONG).show();return;
            }
            try{
                for(Generation item:generations)if(ids.contains(item.id)&&item.nsfw&&!protectCharacterCovers(item.id))throw new java.io.IOException("Falha ao preservar privacidade das referências cadastradas.");
                generationStorage.markDeleted(ids,sanitize);
            }catch(Exception e){storageBusy=false;busy.dismiss();Toast.makeText(this,UiLanguage.t("Não foi possível apagar: ")+e.getMessage(),Toast.LENGTH_LONG).show();return;}
            // The persistent markers are the deletion commit. Even an interrupted index write
            // cannot resurrect these items on next launch or through prompt reuse.
            if(versionViewer!=null){versionViewer.cancelMotion();versionViewer.stopVideo();if(sanitize||clearTrials)versionViewer.dialog.dismiss();}
            handler.removeCallbacks(persistDraft);
            generations.removeIf(item->ids.contains(item.id));restoredInputs.keySet().removeAll(ids);recipeCache.clear();
            if(sanitize||clearTrials){for(Generation item:generations)item.trialSession="";prompterSession=UUID.randomUUID().toString();getPreferences(0).edit().putString("prompter_session",prompterSession).commit();}
            boolean metadata=saveFeed();try{saveRestoredInputs();}catch(Exception e){metadata=false;}
            for(ProjectAlbum project:projects)if(sanitize||ids.contains(project.coverId))project.coverId="";
            metadata&=saveProjects();metadata&=clearGenerationPreferences(ids,sanitize);
            if(mainReferences!=null){mainReferences.ids.removeAll(ids);if(sanitize)mainReferences.ids.clear();mainReferences.save();}
            if(sanitize){bindingFields=true;dockPrompt.setText("");bindingFields=false;result.setText("");stopProgressSocket();}
            else if(ids.contains(progressJob))stopProgressSocket();
            coverCache.evictAll();selectedGenerations.clear();selecting=false;renderFeed();
            final boolean savedMetadata=metadata;
            // A poll already queued behind the barrier may still finish a file write. This
            // cleanup runs after it, while deletion markers stop subsequent downloads.
            worker.execute(()->{
                boolean files=generationStorage.recoverCleanup();
                missingJobs.keySet().removeIf(key->ids.stream().anyMatch(id->key.endsWith("|"+id)));
                downloadFailures.keySet().removeIf(key->ids.stream().anyMatch(id->key.endsWith("|"+id)));
                final boolean clean=files&&savedMetadata;
                runOnUiThread(()->{storageBusy=false;busy.dismiss();renderFeed();Toast.makeText(this,clean?(sanitize?UiLanguage.t("Sanitize concluído. Nenhuma geração no app."):clearTrials?UiLanguage.t("Prompter limpo. Testes salvos preservados."):ids.size()+UiLanguage.t(" itens apagados de todo o app.")):UiLanguage.t("Itens removidos. Alguns arquivos ou ajustes não puderam ser limpos; tente Sanitize novamente."),Toast.LENGTH_LONG).show();});
            });
        }));
    }
    private boolean clearGenerationPreferences(java.util.Set<String> ids,boolean sanitize){
        android.content.SharedPreferences preferences=getPreferences(0);android.content.SharedPreferences.Editor edit=preferences.edit();
        for(Map.Entry<String,?> entry:preferences.getAll().entrySet()){
            String key=entry.getKey();Object value=entry.getValue();
            if(key.startsWith("refs_")){
                if(sanitize){edit.remove(key);continue;}
                try{JSONArray before=new JSONArray(String.valueOf(value)),after=new JSONArray();boolean frames=key.endsWith("_frames");for(int i=0;i<before.length();i++){String id=before.optString(i);if(!ids.contains(id))after.put(id);else if(frames)after.put("");}edit.putString(key,after.toString());}catch(Exception ignored){}
            }
            if((key.startsWith("gallery_cover_")||key.startsWith("reuse_"))&&(sanitize||ids.contains(String.valueOf(value)))){edit.remove(key);edit.remove("options_"+key);}
            if(sanitize&&(key.contains("draft_")||key.startsWith("pending_reference_")||key.startsWith("options_reuse_")))edit.remove(key);
            if(!sanitize)for(String id:ids)if(key.equals("edit_draft_"+id)||key.equals("video_edit_draft_"+id)||key.equals("video_extend_draft_"+id)||key.equals("video_extend_range_"+id)||key.equals("animation_draft_"+id))edit.remove(key);
        }
        return edit.commit();
    }

    private Bitmap coverBitmap(Generation item) {
        galleryAspect(item);
        int[] size=StealthCoverPolicy.dimensions(item.mediaWidth,item.mediaHeight,1280);
        List<Generation> safe=StealthCoverPolicy.candidates(generations,item.id,item.workflowName,new StealthCoverPolicy.Access<Generation>(){
            public String id(Generation value){return value.id;}
            public String workflow(Generation value){return value.workflowName;}
            public boolean safeAndReady(Generation value){return !value.temporary&&!value.nsfw&&"done".equals(value.state)&&thumbFile(value.id).isFile();}
        });
        for(Generation source:safe){
            File file="video".equals(source.mediaKind)?thumbFile(source.id):imageFile(source.id);
            if(!file.isFile())file=thumbFile(source.id);
            Bitmap bitmap=fitCover(file.getAbsolutePath(),false,size);if(bitmap!=null)return bitmap;
        }
        File[] custom=getPreferences(0).getBoolean("custom_covers_"+item.workflowName,false)?customCoverFiles(item.workflowName):new File[0];
        for(int i=0;i<custom.length;i++){
            File file=custom[(Math.floorMod(item.id.hashCode(),custom.length)+i)%custom.length];
            Bitmap bitmap=fitCover(file.getAbsolutePath(),false,size);if(bitmap!=null)return bitmap;
        }
        for(int i=0;i<DEFAULT_COVERS.length;i++){
            String asset=DEFAULT_COVERS[(Math.floorMod(item.id.hashCode(),DEFAULT_COVERS.length)+i)%DEFAULT_COVERS.length];
            Bitmap bitmap=fitCover(asset,true,size);if(bitmap!=null)return bitmap;
        }
        Bitmap fallback=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.RGB_565);fallback.eraseColor(FIELD);return fallback;
    }
    private Bitmap fitCover(String source,boolean asset,int[] size){
        String key=source+":"+(asset?0:new File(source).lastModified())+":"+size[0]+"x"+size[1];
        Bitmap cached=coverCache.get(key);if(cached!=null)return cached;
        Bitmap original=null;
        try{
            if(!asset)original=viewerBitmap(new File(source));
            else try(InputStream input=getAssets().open(source)){original=BitmapFactory.decodeStream(input);}
            if(original==null)return null;
            Bitmap fitted=Bitmap.createBitmap(size[0],size[1],Bitmap.Config.RGB_565);
            android.graphics.Canvas canvas=new android.graphics.Canvas(fitted);
            float scale=Math.max((float)size[0]/original.getWidth(),(float)size[1]/original.getHeight());
            float width=original.getWidth()*scale,height=original.getHeight()*scale;
            canvas.drawBitmap(original,null,new android.graphics.RectF((size[0]-width)/2,(size[1]-height)/2,(size[0]+width)/2,(size[1]+height)/2),new android.graphics.Paint(3));
            coverCache.put(key,fitted);return fitted;
        }catch(Exception ignored){return null;}finally{if(original!=null)original.recycle();}
    }

    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(referenceStudio!=null)referenceStudio.audio.permission(request,grants);}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);syncFeedPlayback();}
    @Override protected void onResume(){super.onResume();if(!vaultUiReady)return;appResumed=true;if(motionLight!=null)motionLight.start(getPreferences(0).getBoolean("motion_light",true));if(serverMonitor!=null)serverMonitor.resume();if(gallery!=null)gallery.setPlaybackEnabled(galleryAutoplay);cleanupExpiredTrials();if(versionViewer!=null)versionViewer.refresh();if(mainScroll!=null)mainScroll.post(()->syncFeedPlayback());}

    @Override protected void onPause() {
        if(referenceStudio!=null)referenceStudio.audio.pause();
        resetVersionTaps();
        if(referenceStudio!=null)referenceStudio.extractor.conceal();
        if(!vaultUiReady){super.onPause();return;}
        if(motionLight!=null)motionLight.stop();
        if(serverMonitor!=null)serverMonitor.pause();
        appResumed=false;stopProgressSocket();if(gallery!=null)gallery.setPlaybackEnabled(false);syncFeedPlayback();
        handler.removeCallbacks(persistDraft);
        saveDraft();
        hideGalleryReveal();
        for (Runnable hide : hideReveals) hide.run();
        if (versionViewer != null) { versionViewer.cancelMotion();versionViewer.saveEditDraft(); versionViewer.conceal(); versionViewer.stopVideo(); }
        super.onPause();
    }

    private void openViewer(Generation item) {
        if (!visibleGeneration(item)) return;
        if (versionViewer != null) versionViewer.dialog.dismiss();
        hideGalleryReveal();
        for (Runnable hide : hideReveals) hide.run();
        versionViewer = new VersionViewer(item);
        versionViewer.show();
    }

    private String familyId(Generation item) {
        return MediaFamilies.root(item,generations);
    }

    private String uploadEditInput(String address, File file) throws Exception {
        if (!file.exists()) throw new IllegalStateException(UiLanguage.t("Imagem original não está salva neste aparelho."));
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        VaultFiles.bitmap(file.getAbsolutePath(), options);
        String mime = options.outMimeType;
        if (mime == null || !mime.startsWith("image/")) throw new IllegalStateException(UiLanguage.t("Imagem de entrada inválida."));
        String extension = "image/jpeg".equals(mime) ? ".jpg" : "image/webp".equals(mime) ? ".webp" : ".png";
        return uploadMediaInput(address,file,mime,extension);
    }

    private String uploadMediaInput(String address,File file,String mime,String extension)throws Exception {
        String filename = "momentos_" + UUID.randomUUID() + extension;
        String boundary = "Momentos" + UUID.randomUUID().toString().replace("-", "");
        HttpURLConnection connection = DesktopConnection.open(this,address + "/upload/image");
        connection.setRequestMethod("POST"); connection.setConnectTimeout(10000); connection.setReadTimeout(60000);
        connection.setDoOutput(true); connection.setChunkedStreamingMode(8192);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        try {
            try (OutputStream output = connection.getOutputStream(); InputStream input = VaultFiles.open(file)) {
                String fields = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"type\"\r\n\r\ninput\r\n"
                        + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"subfolder\"\r\n\r\nMomentos/Edits\r\n"
                        + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"" + filename
                        + "\"\r\nContent-Type: " + mime + "\r\n\r\n";
                output.write(fields.getBytes(StandardCharsets.UTF_8));
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            if(code>=300&&code<400)throw new IOException(UiLanguage.t("Redirecionamento de upload recusado."));
            byte[] bytes = read(code >= 400 ? connection.getErrorStream() : connection.getInputStream(), 1000000);
            if (code >= 400) throw new IllegalStateException(UiLanguage.t("Falha ao enviar referência (HTTP ") + code + ").");
            JSONObject uploaded = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            String name = uploaded.getString("name"), folder = uploaded.optString("subfolder", "");
            if (!name.matches("[A-Za-z0-9_.-]+") || name.contains("..") || folder.contains("..") || folder.contains("\\")
                    || folder.startsWith("/") || folder.contains(":")) throw new IllegalStateException(UiLanguage.t("Caminho de upload inválido."));
            return (folder.isEmpty() ? "" : folder + "/") + name;
        } finally { connection.disconnect(); }
    }

    private void openVideoExtension(VersionViewer owner){
        Generation source=findGeneration(owner.currentId);
        if(source==null||!"video".equals(source.mediaKind)||!"done".equals(source.state))return;
        String unavailable=MemoryProfiles.extensionUnavailable(memoryProfile());
        if(!unavailable.isEmpty()){Toast.makeText(this,unavailable,Toast.LENGTH_LONG).show();return;}
        File file=videoFile(source.id);if(!file.isFile()){Toast.makeText(this,UiLanguage.t("Vídeo ainda não está disponível no aparelho."),Toast.LENGTH_LONG).show();return;}
        owner.stopVideo();
        worker.execute(()->{try{
            VideoClip clip=VideoClip.read(file,false);if(clip.millis<1000)throw new IllegalArgumentException(UiLanguage.t("A extensão precisa de pelo menos 1 segundo de vídeo."));
            JSONObject range=owner.extending?GenerationRecipe.copy(owner.extensionRange):new JSONObject(getPreferences(0).getString("video_extend_range_"+source.id,"{}"));
            if(!range.has("end"))range=VideoExtendWorkflow.range(Math.max(0,clip.millis-120000),clip.millis);
            final JSONObject selected=range;
            runOnUiThread(()->{if(!owner.dialog.isShowing()||!source.id.equals(owner.currentId))return;
                VideoRangeEditor.show(this,file,clip.millis,selected,covered(source)?coverBitmap(source):galleryThumbnail(thumbFile(source.id)),covered(source),source.nsfw,clip.width/(double)clip.height,trim->{
                    owner.saveEditDraft();owner.extending=true;owner.extensionRange=trim;owner.animationEditor=true;owner.clipRatio="Original";
                    if(owner.editDurationIndex<0)owner.editDurationIndex=1;
                    getPreferences(0).edit().putString("video_extend_range_"+source.id,trim.toString()).apply();
                    owner.bindDraft();owner.references.replace(java.util.Collections.singletonList(source.id));UiMotion.hide(owner.optionPanel);owner.refresh();
                });
            });
        }catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}});
    }

    private void submitVideoEdit(VersionViewer owner){
        Generation source=findGeneration(owner.currentId);
        if(source==null||!owner.editingVideo()||owner.videoSubmitting||editUploads.contains(source.id))return;
        String draft=owner.instruction.getText().toString().trim();
        if(draft.isEmpty()){owner.instruction.setError(UiLanguage.t("Descreva a alteração no vídeo."));return;}
        if(!connected||baseUrl==null||!baseUrl.equals(serverInput.getText().toString().trim().replaceAll("/+$",""))){owner.status.setText(UiLanguage.t("Conecte ao servidor em Ajustes."));return;}
        final List<Generation> refs;try{refs=owner.references.snapshot();}catch(Exception e){owner.status.setText(e.getMessage());return;}
        final List<ReferencePreset> presets=taggedReferences(draft);
        final String address=baseUrl,expanded=PromptTags.expand(draft,tagDescriptions()),ratio=owner.outputRatio();
        final int requested=owner.batch,seconds=owner.editDurationIndex<0?0:new int[]{3,5,8,10}[owner.editDurationIndex];
        final boolean extending=owner.extending,turbo=owner.editTurbo,privateResult=!hideAllNsfw||source.nsfw||refs.stream().anyMatch(ref->ref.nsfw)||ReferenceBindings.hidden(presets);
        final double megapixels=GenerationOptions.videoMegapixels(owner.videoQuality);
        final Destination destination=new Destination(owner);final String advanced=viewerAdvancedValue(owner);
        final String loras=getPreferences(0).getString(loraContextKey(owner),"");
        final JSONObject template,recipe;try{template=editTemplate(owner);recipe=captureRecipe(owner,template,extending?"video_extend":"video_edit",refs,presets,draft,expanded,privateResult);}catch(Exception e){owner.status.setText(UiLanguage.t("Falha ao guardar configuração: ")+e.getMessage());return;}
        owner.saveEditDraft();owner.videoSubmitting=true;editUploads.add(source.id);owner.send.setEnabled(false);
        owner.status.setText(extending?UiLanguage.t("Preparando trecho para extensão…"):UiLanguage.t("Enviando clipe para editar…"));hideKeyboard(owner.instruction);renderFeed();
        worker.execute(()->{int accepted=0;
            try{
                File clipFile=videoFile(source.id);VideoClip clip=VideoClip.read(clipFile,!extending);
                JSONObject graph=GenerationRecipe.copy(template);
                String uploaded=uploadMediaInput(address,clipFile,clip.mime,"video/webm".equals(clip.mime)?".webm":".mp4");
                List<String> images=new ArrayList<>();for(Generation ref:refs)if(!ref.id.equals(source.id))images.add(uploadEditInput(address,imageFile(ref.id)));
                if(extending){if(ReferenceBindings.hasVisual(presets,address))throw new IllegalArgumentException(UiLanguage.t("Para estender, use o clipe como referência visual. Desative imagens e RefMods nas tags usadas; textos e LoRAs continuam disponíveis."));VideoExtendWorkflow.prepare(graph,uploaded,expanded,recipe.getJSONObject("options").getJSONObject("videoRange"),clip.width,clip.height,clip.millis,megapixels,seconds==0?5:seconds,new java.util.Random().nextLong());}
                else VideoEditWorkflow.prepare(graph,uploaded,images,clip.audio,turbo,expanded,ratio,megapixels,clip.width,clip.height,clip.millis,seconds,new java.util.Random().nextLong());
                if(!loras.isEmpty())applySelectedLoras(graph,loras,address);
                applyReferenceAssets(graph,presets,address,Collections.emptyList(),refs);
                GenerationRecipe.preserveOutput(graph,recipe);AdvancedGeneration.apply(graph,new JSONObject(advanced));prepareAgent(graph,recipe,address);
                for(int index=0;index<requested;index++){
                    JSONObject variant=new JSONObject(graph.toString());GenerationOptions.varySeed(variant,index);
                    String id=requestJson("POST",address+"/prompt",new JSONObject().put("prompt",variant).put("client_id",UUID.randomUUID().toString())).optString("prompt_id");
                    if(!validId(id))throw new IllegalStateException(UiLanguage.t("Resposta sem ID de geração."));accepted++;final boolean last=index==requested-1;
                    List<String> allInputs=new ArrayList<>();allInputs.add(uploaded);allInputs.addAll(images);saveRecipe(id,recipe,variant,allInputs);
                    runOnUiThread(()->{
                        Generation item=new Generation();item.id=id;item.mediaKind="video";MediaFamilies.videoEdit(item,source,generations);
                        item.workflowName=extending?VideoExtendWorkflow.name(turbo):VideoEditWorkflow.name(turbo);item.serverAddress=address;item.prompt=expanded;item.promptTemplate=draft;
                        item.state="queued";item.createdAt=System.currentTimeMillis();item.nsfw=privateResult;generations.add(0,item);
                        item.settings=GenerationDetails.snapshot(variant);destination.apply(item,source);
                        if(!saveFeed())Toast.makeText(this,UiLanguage.t("Edit enviado; falha ao salvar histórico local."),Toast.LENGTH_LONG).show();
                        if(last&&owner.dialog.isShowing()&&owner.currentId.equals(source.id)&&owner.editingVideo()){
                            if(!owner.prompterContext&&owner.instruction.getText().toString().trim().equals(draft))owner.instruction.setText("");owner.select(id);
                        }
                        renderFeed();result.setText(extending?UiLanguage.t("Extensão enviada. Acompanhe nas versões."):UiLanguage.t("Edit de vídeo enviado. Acompanhe nas versões."));
                    });
                }
            }catch(Exception e){final int sent=accepted;runOnUiThread(()->owner.status.setText((extending?UiLanguage.t("Falha ao estender vídeo: "):UiLanguage.t("Falha ao editar vídeo: "))+e.getMessage()+" · "+sent+UiLanguage.t(" de ")+requested+UiLanguage.t(" confirmados. Confira as versões antes de repetir.")));}
            finally{runOnUiThread(()->{owner.videoSubmitting=false;editUploads.remove(source.id);if(owner.dialog.isShowing())owner.refresh();renderFeed();});}
        });
    }

    private void videoError(VersionViewer owner,String message){if(owner==null)result.setText(message);else owner.status.setText(message);}
    private void submitVideo(VersionViewer owner){
        if(owner==null?videoSubmitting:owner.videoSubmitting)return;
        EditText input=owner==null?dockPrompt:owner.instruction;String draft=input.getText().toString().trim();
        if(draft.isEmpty()){input.setError(UiLanguage.t("Descreva seu vídeo."));return;}
        if(!connected||baseUrl==null||!baseUrl.equals(serverInput.getText().toString().trim().replaceAll("/+$",""))){videoError(owner,UiLanguage.t("Conecte ao servidor em Ajustes."));return;}
        ReferenceTray tray=videoTray(owner);final List<Generation> refs;try{refs=tray.snapshot();}catch(Exception e){videoError(owner,e.getMessage());return;}
        final boolean turbo=owner==null?VideoWorkflow.turbo(workflow):owner.turbo,firstLast=tray.framesMode();
        final String address=baseUrl,context=tray.key(),anchor=owner==null?"":owner.currentId,name=turbo?VideoWorkflow.TURBO:VideoWorkflow.STANDARD;
        final int count=owner==null?batchCount:owner.batch,seconds=new int[]{3,5,8,10}[owner==null?videoDuration:owner.durationIndex];
        final List<ReferencePreset> presets=taggedReferences(draft);
        final String ratio=effectiveVideoRatio(owner),expanded=PromptTags.expand(draft,tagDescriptions());
        final double megapixels=GenerationOptions.videoMegapixels(owner==null?videoQuality:owner.videoQuality);
        final boolean privateResult=!hideAllNsfw||refs.stream().anyMatch(item->item.nsfw)||ReferenceBindings.hidden(presets);
        final JSONObject template,recipe;final String loras;final Destination destination=new Destination(owner);
        final String advanced=owner==null?advancedValue(workflow,workflowName):viewerAdvancedValue(owner);
        try{template=owner==null?new JSONObject(workflow.toString()):editTemplate(owner);loras=getPreferences(0).getString(loraKey(template,name),"");recipe=captureRecipe(owner,template,"video",refs,presets,draft,expanded,privateResult);}
        catch(Exception e){videoError(owner,UiLanguage.t("Falha ao abrir workflow de vídeo."));return;}
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;if(!refs.isEmpty())VaultFiles.bitmap(thumbFile(refs.get(0).id).getAbsolutePath(),bounds);
        final int width=bounds.outWidth,height=bounds.outHeight;
        if(owner==null){videoSubmitting=true;runButton.setEnabled(false);}else{owner.videoSubmitting=true;owner.send.setEnabled(false);owner.saveEditDraft();}
        hideKeyboard(input);videoError(owner,UiLanguage.t("Enviando vídeo…"));updateGenerationStatus();
        worker.execute(()->{int accepted=0;
            try{
                List<String> uploads=new ArrayList<>();for(Generation ref:refs)uploads.add(uploadEditInput(address,imageFile(ref.id)));
                VideoWorkflow.prepare(template,uploads,firstLast,expanded,ratio,megapixels,width,height,seconds,new java.util.Random().nextLong());
                prepareTaggedVideo(template,presets,!uploads.isEmpty(),turbo,address);
                if(!loras.isEmpty())applySelectedLoras(template,loras,address);
                applyReferenceAssets(template,presets,address,Collections.emptyList(),refs);
                GenerationRecipe.preserveOutput(template,recipe);AdvancedGeneration.apply(template,new JSONObject(advanced));prepareAgent(template,recipe,address);
                for(int i=0;i<count;i++){
                    JSONObject graph=new JSONObject(template.toString());GenerationOptions.varySeed(graph,i);String id=requestJson("POST",address+"/prompt",new JSONObject().put("prompt",graph).put("client_id",UUID.randomUUID().toString())).optString("prompt_id");
                    if(!validId(id))throw new IllegalStateException(UiLanguage.t("Resposta sem ID de geração."));accepted++;final boolean last=i==count-1;
                    saveRecipe(id,recipe,graph,uploads);
                    runOnUiThread(()->{
                        Generation item=new Generation();item.id=id;item.rootId=id;item.sourceId=refs.isEmpty()?"":refs.get(0).id;item.workflowName=name;item.serverAddress=address;
                        item.prompt=expanded;item.promptTemplate=draft;item.mediaKind="video";item.state="queued";item.createdAt=System.currentTimeMillis();item.nsfw=privateResult;
                        item.settings=GenerationDetails.snapshot(graph);destination.apply(item,owner==null?null:findGeneration(anchor));
                        generations.add(0,item);if(!saveFeed())Toast.makeText(this,UiLanguage.t("Vídeo enviado; falha ao salvar histórico local."),Toast.LENGTH_LONG).show();
                        if(last){
                            if(owner==null&&context.equals(mainReferences.key())){if(destination.session.isEmpty()&&dockPrompt.getText().toString().trim().equals(draft))dockPrompt.setText("");saveDraft();UiMotion.hide(quickOptions);}
                            else if(owner!=null&&owner.dialog.isShowing()&&owner.animationEditor&&owner.turbo==turbo&&anchor.equals(owner.currentId)&&context.equals(owner.references.key())){if(!owner.prompterContext&&owner.instruction.getText().toString().trim().equals(draft))owner.instruction.setText("");owner.select(id);}
                        }
                        renderFeed();result.setText(UiLanguage.t("Vídeo enviado como nova geração."));
                    });
                }
            }catch(Exception e){final int sent=accepted;runOnUiThread(()->videoError(owner,UiLanguage.t("Falha no envio de vídeo: ")+e.getMessage()+" · "+sent+UiLanguage.t(" de ")+count+UiLanguage.t(" confirmados. Confira a fila antes de repetir.")));}
            finally{runOnUiThread(()->{if(owner==null){videoSubmitting=false;runButton.setEnabled(true);}else{owner.videoSubmitting=false;if(owner.dialog.isShowing())owner.refresh();}updateGenerationStatus();});}
        });
    }


    private void submitVariation(Generation source, String instruction, boolean makePrivate, boolean animate, int frames, VersionViewer owner) {
        if(animate){submitVideo(owner);return;}
        if (editUploads.contains(source.id)) return;
        if (instruction.isEmpty()) { owner.instruction.setError(UiLanguage.t("Descreva a mudança.")); return; }
        if (!connected || baseUrl == null || !baseUrl.equals(serverInput.getText().toString().trim().replaceAll("/+$", ""))) {
            owner.status.setText(UiLanguage.t("Conecte ao servidor em Ajustes antes de editar.")); return;
        }
        if ("video".equals(source.mediaKind) || !"done".equals(source.state) || !imageFile(source.id).exists()) {
            owner.status.setText(UiLanguage.t("Esta versão ainda não tem imagem disponível.")); return;
        }
        final List<Generation> referenceImages;
        try{referenceImages=owner.references.snapshot();if(referenceImages.isEmpty()||!referenceImages.get(0).id.equals(source.id))throw new IllegalArgumentException(UiLanguage.t("Imagem base indisponível."));}
        catch(Exception e){owner.status.setText(e.getMessage());return;}
        final List<ReferencePreset> presets=taggedReferences(instruction);
        final boolean privateResult=makePrivate||referenceImages.stream().anyMatch(item->item.nsfw)||ReferenceBindings.hidden(presets);
        final String address = baseUrl;
        final String root = familyId(source);
        final int requested = owner.batch;
        final String outputRatio = owner.outputRatio();
        final double resolution=GenerationOptions.videoMegapixels(owner.videoQuality);
        final int imageMegapixels=owner.imageMegapixels;final Destination destination=new Destination(owner);final String advanced=viewerAdvancedValue(owner);
        final String loras=getPreferences(0).getString(loraContextKey(owner),"");
        BitmapFactory.Options referenceBounds=new BitmapFactory.Options();referenceBounds.inJustDecodeBounds=true;
        VaultFiles.bitmap(thumbFile(source.id).getAbsolutePath(),referenceBounds);
        final int referenceWidth=referenceBounds.outWidth,referenceHeight=referenceBounds.outHeight;
        final String expandedInstruction=PromptTags.expand(instruction,tagDescriptions());
        final JSONObject template,recipe;try{template=editTemplate(owner);recipe=captureRecipe(owner,template,"image_edit",referenceImages,presets,instruction,expandedInstruction,privateResult);}catch(Exception e){owner.status.setText(UiLanguage.t("Falha ao guardar configuração: ")+e.getMessage());return;}
        editUploads.add(source.id); owner.saveEditDraft(); owner.status.setText(animate ? UiLanguage.t("Enviando referência para animar…") : UiLanguage.t("Enviando imagem para editar…")); renderFeed();
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(owner.instruction.getWindowToken(), 0);
        worker.execute(() -> {
            try {
                JSONObject graph=GenerationRecipe.copy(template);
                List<String> uploaded=new ArrayList<>();for(Generation ref:referenceImages)uploaded.add(uploadEditInput(address,imageFile(ref.id)));
                if(!loras.isEmpty())applySelectedLoras(graph,loras,address);
                applyReferenceAssets(graph,presets,address,uploaded,referenceImages);
                if (animate) {
                    graph.getJSONObject("8").getJSONObject("inputs").put("prompt", animationPrompt(expandedInstruction)).put("length", frames);
                    graph.getJSONObject("9").getJSONObject("inputs").put("noise_seed", new java.util.Random().nextLong() & Long.MAX_VALUE);
                } else {
                    graph.getJSONObject("6").getJSONObject("inputs").put("text", expandedInstruction);
                    graph.getJSONObject("12").getJSONObject("inputs").put("noise_seed", new java.util.Random().nextLong() & Long.MAX_VALUE);
                }
                if(animate)GenerationOptions.videoOutput(graph,outputRatio,resolution,referenceWidth,referenceHeight);
                else GenerationOptions.imageOutput(graph,outputRatio,imageMegapixels,referenceWidth,referenceHeight);
                GenerationRecipe.preserveOutput(graph,recipe);AdvancedGeneration.apply(graph,new JSONObject(advanced));prepareAgent(graph,recipe,address);
                for(int index=0;index<requested;index++) {
                JSONObject variant=new JSONObject(graph.toString());GenerationOptions.varySeed(variant,index);
                JSONObject body = new JSONObject(); body.put("prompt", variant); body.put("client_id", UUID.randomUUID().toString());
                final boolean last=index==requested-1;
                String id = requestJson("POST", address + "/prompt", body).optString("prompt_id");
                if (!validId(id)) throw new IllegalStateException(UiLanguage.t("ComfyUI retornou ID inválido."));
                saveRecipe(id,recipe,variant,uploaded);
                runOnUiThread(() -> {
                    Generation item = new Generation(); item.id = id; item.serverAddress = address;
                    if(animate){item.rootId=id;item.sourceId=source.id;item.workflowName="MiniMax H3";}
                    else{item.rootId=root;item.parentId=source.id;item.sourceId=source.id;for(Generation version:versions(root))item.editNumber=Math.max(item.editNumber,version.editNumber);item.editNumber++;item.workflowName=source.workflowName;}
                    item.prompt = expandedInstruction;item.promptTemplate=instruction;
                    item.mediaKind = animate ? "video" : "image";
                    item.state = "queued"; item.createdAt = System.currentTimeMillis(); item.nsfw = privateResult;
                    item.settings=GenerationDetails.snapshot(variant);destination.apply(item,source);
                    generations.add(0, item);
                    if (!saveFeed()) Toast.makeText(this, UiLanguage.t("Edit enviado. Falha ao salvar histórico local."), Toast.LENGTH_LONG).show();
                    if (last && owner.dialog.isShowing() && owner.currentId.equals(source.id) && owner.animationEditor == animate) {
                        if (!owner.prompterContext&&owner.instruction.getText().toString().trim().equals(instruction)) owner.instruction.setText("");
                        owner.select(id);
                    }
                    renderFeed(); result.setText(animate ? UiLanguage.t("Vídeo enviado como nova geração.") : UiLanguage.t("Edit enviado. Acompanhe em Versões."));
                });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    editUploads.remove(source.id); renderFeed();
                    String message = (animate ? "Falha ao animar: " : "Falha ao editar: ") + e.getMessage();
                    owner.status.setText(message + UiLanguage.t(" Confira as versões já enviadas antes de repetir.")); result.setText(message);
                });
            } finally {runOnUiThread(()->{editUploads.remove(source.id);renderFeed();});}
        });
    }

    private String animationPrompt(String instruction) {
        String alignment = "For the target video, at 0.00 seconds into the target video, <Picture 1> (from [Shot 1]) is fully referenced.";
        if (instruction.contains("integrated_multimodal_description:"))
            return instruction.contains("0.00 seconds") ? instruction : alignment + "\n\n" + instruction;
        return alignment + "\n\nintegrated_multimodal_description: [Shot 1] " + instruction
                + "\nPreserve the appearance and composition of the reference at the beginning. Unless requested otherwise, use one continuous shot. If speech is not explicitly requested, all mouths remain closed."
                + "\n\noverall_soundscape: Follow any explicit sound and dialogue directions above. Otherwise, only subtle sounds from the visible scene. No unrequested spoken dialogue, vocalizations or voiceover."
                + "\n\nnon_diegetic_music: N/A unless explicitly requested above.";
    }

    private int imageOrientation(InputStream input) {
        try(InputStream stream=input){return android.os.Build.VERSION.SDK_INT>=24?new android.media.ExifInterface(stream).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1):1;}
        catch(Exception ignored){return 1;}
    }
    private Bitmap orientBitmap(Bitmap bitmap,int orientation) {
        if(bitmap==null||orientation<=1||orientation>8)return bitmap;
        android.graphics.Matrix matrix=new android.graphics.Matrix();
        switch(orientation){case 2:matrix.setScale(-1,1);break;case 3:matrix.setRotate(180);break;case 4:matrix.setScale(1,-1);break;
            case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;case 6:matrix.setRotate(90);break;case 7:matrix.setRotate(-90);matrix.postScale(-1,1);break;case 8:matrix.setRotate(-90);break;}
        Bitmap oriented=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(oriented!=bitmap)bitmap.recycle();return oriented;
    }
    private Bitmap viewerBitmap(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        VaultFiles.bitmap(file.getAbsolutePath(), options); options.inJustDecodeBounds = false;
        options.inSampleSize = Math.max(1, (Math.max(options.outWidth, options.outHeight) + 2199) / 2200);
        Bitmap bitmap=VaultFiles.bitmap(file.getAbsolutePath(),options);
        try{return orientBitmap(bitmap,imageOrientation(VaultFiles.open(file)));}catch(Exception ignored){return bitmap;}
    }

    private String versionName(Generation item) {
        return item.editNumber == 0 ? "Original" : "Edit " + item.editNumber;
    }

    private List<Generation> versions(String rootId) {
        List<Generation> items = new ArrayList<>();
        for (Generation item : generations) if (familyId(item).equals(rootId)) items.add(item);
        Collections.sort(items, (a, b) -> {
            int order = Integer.compare(a.editNumber, b.editNumber);
            return order == 0 ? Long.compare(a.createdAt, b.createdAt) : order;
        });
        return items;
    }

    /** Clips the displayed bitmap, rather than the letterboxed ImageView bounds. */
    private final class RoundedMediaImage extends ImageView {
        private final android.graphics.Path clip = new android.graphics.Path();
        private final android.graphics.RectF bounds = new android.graphics.RectF();
        private final float[] transform=new float[9];
        private final android.graphics.Paint checkerPaint=new android.graphics.Paint();
        private GenerationProgress.Display lastProgress;
        RoundedMediaImage() { super(MainActivity.this); }
        @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)invalidate();}
        @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(visibility==View.VISIBLE)invalidate();}
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            android.graphics.drawable.Drawable drawable = getDrawable();
            if (drawable == null) { super.onDraw(canvas); return; }
            boolean animate=false;
            if(drawable instanceof GenerationArtwork){
                GenerationArtwork artwork=(GenerationArtwork)drawable;getImageMatrix().getValues(transform);artwork.setUiScale(getResources().getDisplayMetrics().density/Math.max(.001f,Math.abs(transform[0])));
                animate=android.os.Build.VERSION.SDK_INT>=26?android.animation.ValueAnimator.areAnimatorsEnabled():android.provider.Settings.Global.getFloat(getContentResolver(),android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1)>0;
                artwork.animate=animate;
                if(artwork.source!=null){GenerationProgress.Display display=artwork.source.read();if(display!=lastProgress){lastProgress=display;setContentDescription(display.title+". "+display.detail+UiLanguage.t(". Deslize para navegar."));}}
            }
            bounds.set(drawable.getBounds());
            getImageMatrix().mapRect(bounds);
            bounds.offset(getPaddingLeft(), getPaddingTop());
            if (!bounds.intersect(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom())) return;
            clip.reset(); clip.addRoundRect(bounds, dp(24), dp(24), android.graphics.Path.Direction.CW);
            int saved = canvas.save(); canvas.clipPath(clip);
            if(drawable instanceof android.graphics.drawable.BitmapDrawable&&((android.graphics.drawable.BitmapDrawable)drawable).getBitmap().hasAlpha())OutpaintSheet.checker(canvas,bounds,dp(12),checkerPaint);
            super.onDraw(canvas); canvas.restoreToCount(saved);
            if(drawable instanceof GenerationArtwork&&isShown()&&hasWindowFocus()&&getWindowVisibility()==View.VISIBLE)postInvalidateDelayed(animate?33:1000);
        }
    }

    private final class VersionViewer {
        final Dialog dialog = new Dialog(MainActivity.this, android.R.style.Theme_Material_NoActionBar){
            @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused){cancelMotion();conceal();stopVideo();}else if(isShowing())refresh();}
        };
        String rootId;
        String currentId;
        final boolean prompterContext=selectedTab==2;
        final Button like=button("",Color.TRANSPARENT,TEXT);
        List<Generation> items = new ArrayList<>();
        final ImageView image = new RoundedMediaImage();
        final InlineVideoPlayer video = new InlineVideoPlayer(MainActivity.this);
        final VideoPreviewCache videoPreviews=new VideoPreviewCache();
        final TextView caption = text("", 13, MUTED, false);
        final TextView status = text("", 12, MUTED, false);
        final Button info = button("", FIELD, TEXT);
        final Button delete = button(UiLanguage.t("Excluir"), FIELD, TEXT), send = button(UiLanguage.t("Gerar edit"), ACCENT, BG);
        final Button reuse = button(UiLanguage.t("Reutilizar prompt"), Color.TRANSPARENT, TEXT);
        
        int durationIndex=Math.max(0,Math.min(3,getPreferences(0).getInt("animation_duration",1)));
        int videoQuality=getPreferences(0).getInt("animation_quality",33);
        int imageMegapixels=GenerationOptions.imageMegapixels(getPreferences(0).getInt("variation_image_mp",1));
        String videoRatio=getPreferences(0).getString("animation_ratio","Original");
        String outputRatio(){return animationEditor?effectiveVideoRatio(this):ratio;}
        final LinearLayout editor = column(), footer = column();
        final EditText instruction = new TagInput();
        final ReferenceTray references=new ReferenceTray(this);

        final ProgressBar progress = new ProgressBar(MainActivity.this, null, android.R.attr.progressBarStyleHorizontal);
        boolean revealed;
        boolean animationEditor;
        boolean extending;
        JSONObject extensionRange=new JSONObject();
        boolean editTurbo=getPreferences(0).getBoolean("video_edit_turbo",true);
        String clipRatio=getPreferences(0).getString("video_edit_ratio","Original");
        int editDurationIndex=Math.max(-1,Math.min(3,getPreferences(0).getInt("video_edit_duration",-1)));
        boolean editingVideo(){Generation item=findGeneration(currentId);return item!=null&&"video".equals(item.mediaKind);}
        String draftKey(){return (extending?"video_extend_draft_":editingVideo()?"video_edit_draft_":animationEditor?"animation_draft_":"edit_draft_")+currentId;}
        boolean firstLastFrame=getPreferences(0).getBoolean("animation_first_last",false), turbo=getPreferences(0).getBoolean("animation_turbo",true), videoSubmitting;
        int batch=GenerationOptions.count(getPreferences(0).getInt("variation_batch",1));
        String ratio=getPreferences(0).getString("variation_ratio","Original");
        final LinearLayout optionPanel=column();
        final Button modeChip=new FlowDesign.Chip(MainActivity.this);
        String boundDraftId="";
        final LinearLayout versionRail=column();
        float touchX,touchY;
        final ImageView incoming=new RoundedMediaImage();
        int dragAxis,motionToken;
        boolean sliding, draggingVideo;
        String dragTarget="";
        float dragOffset,slideExtent;
        void updateModeChip(){((FlowDesign.Chip)modeChip).update(outputRatio(),batch,animationEditor);}
        Button agent,loras;
        void rebuildOptions(){optionPanel.removeAllViews();fillOptions(optionPanel,this);updateModeChip();updateAgentChip(agent,this);updateViewerLoras(this);}

        VersionViewer(Generation first) { rootId = familyId(first); currentId = first.id;animationEditor="video".equals(first.mediaKind); }

        void show() {
            LinearLayout page = column(); page.setBackground(Appearance.atmosphere());
            LinearLayout bar = row();bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(dp(8), dp(4), dp(8), dp(4));
            Button close = button("‹", Color.TRANSPARENT, TEXT); close.setTextSize(28);
            close.setContentDescription(UiLanguage.t("Fechar visualização")); close.setOnClickListener(v -> dialog.dismiss());
            close.setText("⌄");close.setBackground(round(0xDD161719,20,0x22FFFFFF));
            info.setBackgroundColor(Color.TRANSPARENT);FlowDesign.symbol(info,"info",UiLanguage.t("Informações da geração"));bar.addView(info,new LinearLayout.LayoutParams(dp(48),dp(48)));
            info.setOnClickListener(v->{Generation item=findGeneration(currentId);if(item!=null)openGenerationInfo(item);});
            if(prompterContext){bar.addView(like,new LinearLayout.LayoutParams(dp(48),dp(48)));like.setOnClickListener(v->{Generation item=findGeneration(currentId);if(item!=null)saveTrial(item);});}
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackgroundColor(Color.TRANSPARENT);
            image.setPadding(0,0,0,0);
            image.setContentDescription(UiLanguage.t("Imagem. Deslize verticalmente para versões, horizontalmente para outro conjunto."));
            android.widget.FrameLayout media = new android.widget.FrameLayout(MainActivity.this);
            media.setBackgroundColor(Color.TRANSPARENT);

            android.widget.FrameLayout viewport=new android.widget.FrameLayout(MainActivity.this);
            viewport.setClipChildren(true);viewport.setClipToPadding(true);
            android.widget.FrameLayout.LayoutParams viewportPosition=new android.widget.FrameLayout.LayoutParams(-1,-1);
            viewportPosition.setMargins(dp(16),dp(80),dp(16),dp(16));media.addView(viewport,viewportPosition);
            incoming.setScaleType(ImageView.ScaleType.FIT_CENTER);incoming.setPadding(0,0,0,0);incoming.setVisibility(View.GONE);
            viewport.addView(incoming,new android.widget.FrameLayout.LayoutParams(-1,-1));
            viewport.addView(image, new android.widget.FrameLayout.LayoutParams(-1, -1));
            viewport.addView(video,new android.widget.FrameLayout.LayoutParams(-1,-1));
            video.onError=()->{cancelMotion();conceal();stopVideo();status.setText(UiLanguage.t("Não foi possível reproduzir este vídeo."));};
            video.onFirstFrame=()->{if(!sliding&&dragAxis==0&&video.isLoaded(currentId))image.setAlpha(0f);};
            android.widget.FrameLayout.LayoutParams closePosition=new android.widget.FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.LEFT);
            closePosition.setMargins(dp(16),dp(16),0,0);media.addView(close,closePosition);
            page.addView(media, new LinearLayout.LayoutParams(-1, 0, 1));
            android.view.GestureDetector gestures = new android.view.GestureDetector(MainActivity.this,
                    new android.view.GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }
                @Override public void onLongPress(MotionEvent e) { if(dragAxis==0&&!sliding)reveal(); }
                @Override public boolean onDoubleTap(MotionEvent e){if(prompterContext){Generation item=findGeneration(currentId);if(item!=null)saveTrial(item);return true;}return false;}
                @Override public boolean onSingleTapConfirmed(MotionEvent e){if(video.isLoaded(currentId))video.toggleControls();return true;}
            });
            View.OnTouchListener paging=(v,event)->{
                int action=event.getActionMasked();
                if(sliding)return true;
                if(action==MotionEvent.ACTION_DOWN){cancelMotion();touchX=event.getRawX();touchY=event.getRawY();}
                float dx=event.getRawX()-touchX,dy=event.getRawY()-touchY;
                gestures.onTouchEvent(event);
                if(action==MotionEvent.ACTION_MOVE){
                    if(dragAxis==0&&Math.max(Math.abs(dx),Math.abs(dy))>dp(10)){freezeForPaging();dragAxis=Math.abs(dy)>=Math.abs(dx)?1:2;}
                    if(dragAxis!=0)dragPage(dragAxis==1?dy:dx);
                }
                if(action==MotionEvent.ACTION_UP){conceal();if(dragAxis!=0)finishDrag();}
                if(action==MotionEvent.ACTION_CANCEL){conceal();settleBack();}return true;
            };
            image.setOnTouchListener(paging);video.setPagingListener(paging);
            android.widget.FrameLayout.LayoutParams railPosition=new android.widget.FrameLayout.LayoutParams(dp(40),-2,Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            railPosition.rightMargin=dp(4);viewport.addView(versionRail,railPosition);
            FlowDesign.surface(footer);
            LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
            footerParams.setMargins(dp(8),dp(8),dp(8),dp(8));
            page.addView(footer, footerParams);
            progress.setIndeterminate(true); progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(ACCENT));
            footer.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
            caption.setMaxLines(2);caption.setPadding(dp(20),dp(4),dp(20),dp(4));page.addView(caption,page.indexOfChild(footer));
            reuse.setOnClickListener(v -> { Generation item = findGeneration(currentId); if (item != null) reusePrompt(item); });
            reuse.setText("↺");reuse.setContentDescription(UiLanguage.t("Reutilizar prompt"));
            bar.addView(reuse,new LinearLayout.LayoutParams(dp(48),dp(48)));
            delete.setText("⋮");delete.setContentDescription(UiLanguage.t("Mais opções da imagem"));delete.setBackgroundColor(Color.TRANSPARENT);
            bar.addView(delete,new LinearLayout.LayoutParams(dp(48),dp(48)));
            bar.setBackground(round(0xCC161719,24,0x22FFFFFF));
            android.widget.FrameLayout.LayoutParams barPosition=new android.widget.FrameLayout.LayoutParams(-2,dp(48),Gravity.TOP|Gravity.RIGHT);
            barPosition.setMargins(0,dp(16),dp(16),0);media.addView(bar,barPosition);
            delete.setOnClickListener(v -> {
                Generation item = findGeneration(currentId);
                if (item == null) return;
                PopupMenu menu = new OrganicPopupMenu(MainActivity.this, delete);
                addExtractionActions(menu,item);
                if(!"done".equals(item.state))menu.getMenu().add(UiLanguage.t("Verificar resultado")).setOnMenuItemClickListener(m->{verifyResult(item);return true;});
                if("done".equals(item.state)&&thumbFile(item.id).exists())menu.getMenu().add(UiLanguage.t("Criar personagem com esta capa")).setOnMenuItemClickListener(m->{if(item.temporary)saveTrial(item);editCharacter(null,item);return true;});
                if(!hideAllNsfw)menu.getMenu().add(item.nsfw?UiLanguage.t("Remover marcação NSFW"):UiLanguage.t("Marcar como NSFW")).setOnMenuItemClickListener(m->{markPrivate(item);refresh();return true;});
                if(selectedTab==3&&!activeProjectId.isEmpty())menu.getMenu().add(UiLanguage.t("Remover deste projeto")).setOnMenuItemClickListener(m->{String root=familyId(item);List<Generation> changed=new ArrayList<>();for(Generation member:generations)if(familyId(member).equals(root)&&member.projectIds.remove(activeProjectId))changed.add(member);if(!saveFeed())for(Generation member:changed)member.projectIds.add(activeProjectId);renderFeed();return true;});
                if("done".equals(item.state))menu.getMenu().add(UiLanguage.t("Lipsync…")).setOnMenuItemClickListener(choice->{showLipsync(item,null);return true;});
                if("done".equals(item.state))menu.getMenu().add("video".equals(item.mediaKind)?UiLanguage.t("Upscale de vídeo…"):UiLanguage.t("Upscale de imagem…")).setOnMenuItemClickListener(choice->{showUpscale(item,null);return true;});
                if("done".equals(item.state)&&"image".equals(item.mediaKind)){
                    menu.getMenu().add(UiLanguage.t("Remover fundo")).setOnMenuItemClickListener(choice->{showImageTool(item,false,null);return true;});
                    menu.getMenu().add(UiLanguage.t("Expandir imagem")).setOnMenuItemClickListener(choice->{showImageTool(item,true,null);return true;});
                }
                if(editingVideo()&&"done".equals(item.state)){
                    menu.getMenu().add(UiLanguage.t("Estender vídeo…")).setOnMenuItemClickListener(choice->{openVideoExtension(this);return true;});
                    if(extending)menu.getMenu().add(UiLanguage.t("Editar vídeo")).setOnMenuItemClickListener(choice->{saveEditDraft();extending=false;bindDraft();refresh();return true;});
                }
                menu.getMenu().add("video".equals(item.mediaKind)?UiLanguage.t("Excluir vídeo"):UiLanguage.t("Excluir imagem")).setEnabled(canDelete(item)).setOnMenuItemClickListener(choice -> { confirmDelete(item); return true; });
                showIconMenu(menu);
            });

            styleInput(instruction); instruction.setHint(UiLanguage.t("O que você quer alterar?"));
            instruction.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            FlowDesign.prompt(instruction); editor.addView(references);editor.addView(instruction,new LinearLayout.LayoutParams(-1,-2));attachMentions(instruction,editor);

            Button plus=button("+",Color.TRANSPARENT,TEXT);plus.setTextSize(25);plus.setContentDescription(UiLanguage.t("Opções da referência"));

            plus.setOnClickListener(v->showAddMenu(plus));
            modeChip.setOnClickListener(v->{hideKeyboard(instruction);rebuildOptions();UiMotion.toggle(optionPanel);});
            send.setOnClickListener(v -> {
                Generation item = findGeneration(currentId);
                int seconds = new int[]{3, 5, 8, 10}[durationIndex];
                int frames = seconds * 24; frames += (5 - frames % 17 + 17) % 17;
                if(item!=null){if(editingVideo())submitVideoEdit(this);else submitVariation(item, instruction.getText().toString().trim(), !hideAllNsfw, animationEditor, frames, this);}
            });
            send.setContentDescription(UiLanguage.t("Enviar geração"));
            agent=agentChip(this);loras=loraChip(this);editor.addView(FlowDesign.actions(MainActivity.this,plus,loras,agent,modeChip,send));
            editor.setVisibility(View.GONE);footer.addView(editor);status.setPadding(dp(20),dp(4),dp(20),dp(4));status.setGravity(Gravity.CENTER);status.setMaxLines(2);status.setTextSize(11);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);collapseEmpty(status);
            UiMotion.hide(optionPanel);LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,-2);op.setMargins(dp(8),0,dp(8),dp(8));page.addView(optionPanel,op);page.addView(status);
            dialog.setContentView(page);
            dialog.setOnDismissListener(d -> {cancelMotion();saveEditDraft();rememberCover();conceal();stopVideo();videoPreviews.close();if(versionViewer==this)versionViewer=null;rebuildGallerySets();updateGalleryActions();galleryAdapter.notifyDataSetChanged();});
            UiMotion.dialog(dialog);dialog.show();android.view.Window window=dialog.getWindow();
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.BLACK);window.setNavigationBarColor(Color.BLACK);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            if(android.os.Build.VERSION.SDK_INT>=30){
                window.setDecorFitsSystemWindows(false);android.view.WindowInsetsController bars=window.getInsetsController();
                if(bars!=null){bars.show(android.view.WindowInsets.Type.statusBars());bars.setSystemBarsAppearance(0,android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}
            }else window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            page.setOnApplyWindowInsetsListener((v, insets) -> {
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout());
                    android.graphics.Insets ime = insets.getInsets(android.view.WindowInsets.Type.ime());
                    page.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
                    if(insets.isVisible(android.view.WindowInsets.Type.ime())&&instruction.hasFocus())UiMotion.hide(optionPanel);
                } else page.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
                return insets;
            });
            page.requestApplyInsets();bindDraft();refresh();
        }

        void saveEditDraft() {
            if(generationStorage.deleted(currentId))return;
            if (editor.getVisibility() == View.VISIBLE && currentId.equals(boundDraftId)) {
                getPreferences(0).edit().putString(draftKey(), instruction.getText().toString())
                        .putInt("animation_duration", durationIndex).apply();
                if(activeRecipe(this)!=null)try{getPreferences(0).edit().putString("options_"+reuseKey(this),recipeOptions(this,null).toString()).apply();}catch(Exception ignored){}
            }
        }

        void bindDraft() {
            String previous=instruction.getText().toString();
            JSONObject reused=activeRecipe(this);
            if(reused!=null)try{JSONObject settings=new JSONObject(getPreferences(0).getString("options_"+reuseKey(this),reused.getJSONObject("options").toString()));
                batch=GenerationOptions.count(settings.optInt("batch",1));ratio=settings.optString("ratio","Original");videoRatio=ratio;clipRatio=ratio;imageMegapixels=settings.optInt("imageMp",1);videoQuality=settings.optInt("quality",33);durationIndex=Math.max(0,Math.min(3,settings.optInt("duration",1)));editDurationIndex=settings.optInt("duration",-1);firstLastFrame=settings.optBoolean("firstLast");turbo=settings.optBoolean("turbo");editTurbo=turbo;if(extending&&settings.optJSONObject("videoRange")!=null)extensionRange=settings.getJSONObject("videoRange");
            }catch(Exception ignored){}
            boundDraftId=currentId;references.bind();
            instruction.setText(getPreferences(0).getString(draftKey(),prompterContext?previous:""));
            instruction.setHint(extending?UiLanguage.t("Como o vídeo deve continuar?"):editingVideo()?UiLanguage.t("O que você quer alterar no vídeo?"):animationEditor?UiLanguage.t("Descreva movimento, câmera e sons…"):UiLanguage.t("O que você quer alterar?"));
            rebuildOptions();
        }

        void openEditor(boolean animation) {
            Generation item=findGeneration(currentId);
            if(item==null||"video".equals(item.mediaKind))return;
            saveEditDraft();animationEditor=animation;bindDraft();status.setText("");refresh();
        }

        void rememberCover(){
            Generation item=findGeneration(currentId);
            if(item!=null&&!item.temporary&&visibleGeneration(item)&&"done".equals(item.state)&&thumbFile(item.id).exists())
                getPreferences(0).edit().putString("gallery_cover_"+familyId(item),item.id).apply();
        }

        void moveSet(int direction){String target=adjacent(2,direction);if(!target.isEmpty())animateTo(target,2,direction);}

        void updateVersionRail(int selected){
            versionRail.removeAllViews();versionRail.setVisibility(items.size()>1?View.VISIBLE:View.GONE);
            int start=Math.max(0,Math.min(selected-1,items.size()-3)),end=Math.min(items.size(),start+3);
            for(int i=start;i<end;i++){Generation member=items.get(i);final boolean active=i==selected;
                ImageView thumb=new ImageView(MainActivity.this){
                    final android.graphics.Paint ring=new android.graphics.Paint(3);
                    @Override protected void onDraw(android.graphics.Canvas canvas){super.onDraw(canvas);if(active){ring.setColor(TEXT);ring.setStyle(android.graphics.Paint.Style.STROKE);ring.setStrokeWidth(dp(1));float inset=dp(1)/2f;canvas.drawRoundRect(inset,inset,getWidth()-inset,getHeight()-inset,dp(9),dp(9),ring);}}
                };
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);thumb.setPadding(0,0,0,0);thumb.setBackground(round(0xCC202123,9,0));thumb.setClipToOutline(true);
                thumb.setImageBitmap(covered(member)?coverBitmap(member):"done".equals(member.state)?galleryThumbnail(thumbFile(member.id)):null);
                thumb.setAlpha(active?1f:.72f);thumb.setContentDescription(UiLanguage.t("Abrir ")+versionName(member));thumb.setOnClickListener(v->animateTo(member.id,1,items.indexOf(member)>items.indexOf(findGeneration(currentId))?1:-1));
                android.widget.FrameLayout hit=new android.widget.FrameLayout(MainActivity.this);hit.setOnClickListener(v->thumb.performClick());
                hit.setContentDescription(thumb.getContentDescription());thumb.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                hit.addView(thumb,new android.widget.FrameLayout.LayoutParams(dp(32),dp(32),Gravity.CENTER));
                versionRail.addView(hit,new LinearLayout.LayoutParams(dp(40),dp(36)));
            }
        }

        String adjacent(int axis,int direction){
            if(axis==1){for(int i=0;i<items.size();i++)if(items.get(i).id.equals(currentId)){int n=i+direction;return n>=0&&n<items.size()?items.get(n).id:"";}}
            else {if(prompterContext)return "";rebuildGallerySets();for(int i=0;i<gallerySets.size();i++)if(familyId(gallerySets.get(i)).equals(rootId)){int n=i+direction;return n>=0&&n<gallerySets.size()?gallerySets.get(n).id:"";}}
            return "";
        }
        float mediaHeight(ImageView view){
            android.graphics.drawable.Drawable d=view.getDrawable();if(d==null||d.getIntrinsicWidth()<=0||d.getIntrinsicHeight()<=0)return image.getHeight();
            float width=image.getWidth()-view.getPaddingLeft()-view.getPaddingRight(),height=image.getHeight()-view.getPaddingTop()-view.getPaddingBottom();
            return Math.max(1,Math.min(width/d.getIntrinsicWidth(),height/d.getIntrinsicHeight())*d.getIntrinsicHeight());
        }
        float calculatedExtent(int axis){return Math.max(dp(1),axis==1?Math.min(image.getHeight(),(mediaHeight(image)+mediaHeight(incoming))*.5f+dp(16)):image.getWidth());}
        float extent(int axis){return slideExtent>0?slideExtent:calculatedExtent(axis);}
        void translate(View view,int axis,float offset){view.setTranslationX(axis==2?offset:0);view.setTranslationY(axis==1?offset:0);}
        View outgoingMedia(){return draggingVideo?video:image;}
        float swipeProgress(){return Math.max(0f,Math.min(1f,Math.abs(dragOffset)/extent(dragAxis)));}
        void showPreview(ImageView target,Generation item){
            if("queued".equals(item.state)||"running".equals(item.state)){
                String label="queued".equals(item.state)?"Na fila":(item.parentId.isEmpty()?"Gerando ":"Editando ")+("video".equals(item.mediaKind)?"vídeo":"imagem");
                double aspect=galleryAspect(item);android.graphics.drawable.Drawable previous=target.getDrawable();
                if(previous instanceof GenerationArtwork&&Math.abs(((GenerationArtwork)previous).aspect-aspect)<.0001){((GenerationArtwork)previous).label=label;target.invalidate();}
                else target.setImageDrawable(new GenerationArtwork(aspect,label));
                ((GenerationArtwork)target.getDrawable()).source=()->progressDisplay(item);
                target.setContentDescription(progressDisplay(item).title+". "+progressDisplay(item).detail);
            }else target.setImageBitmap(preview(item));
        }
        Bitmap preview(Generation item){
            if("error".equals(item.state)){
                int width=Math.min(1024,getResources().getDisplayMetrics().widthPixels-dp(32)),height=(int)(width*.85f);float scale=width/360f;
                Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.drawColor(FlowDesign.SURFACE);
                android.graphics.Paint icon=new android.graphics.Paint(3);icon.setColor(MUTED);icon.setStyle(android.graphics.Paint.Style.STROKE);icon.setStrokeWidth(1.5f*scale);
                float x=width/2f,y=58*scale;canvas.drawCircle(x,y,17*scale,icon);canvas.drawLine(x,y-8*scale,x,y+3*scale,icon);canvas.drawPoint(x,y+9*scale,icon);
                android.text.TextPaint titlePaint=new android.text.TextPaint(3);titlePaint.setColor(TEXT);titlePaint.setTextSize(18*scale);titlePaint.setTypeface(Branding.body(MainActivity.this,650));
                String failedTitle=UiLanguage.t("Não foi possível gerar");android.text.StaticLayout titleLayout=android.text.StaticLayout.Builder.obtain(failedTitle,0,failedTitle.length(),titlePaint,width-(int)(48*scale)).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build();
                canvas.save();canvas.translate(24*scale,92*scale);titleLayout.draw(canvas);canvas.restore();
                String message=item.error==null||item.error.trim().isEmpty()?UiLanguage.t("Esta geração falhou. Você pode reutilizar o prompt ou excluir esta versão."):item.error;
                android.text.TextPaint body=new android.text.TextPaint(3);body.setColor(MUTED);body.setTextSize(14*scale);
                android.text.StaticLayout detail=android.text.StaticLayout.Builder.obtain(message,0,message.length(),body,width-(int)(128*scale)).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setIncludePad(false).setMaxLines(8).setEllipsize(android.text.TextUtils.TruncateAt.END).build();
                canvas.save();canvas.translate(64*scale,128*scale);detail.draw(canvas);canvas.restore();return bitmap;
            }
            return covered(item)?coverBitmap(item):"done".equals(item.state)?"video".equals(item.mediaKind)?videoPreview(item):viewerBitmap(imageFile(item.id)):null;
        }
        Bitmap videoPreview(Generation item){return videoPreviews.get(videoFile(item.id),()->{
            Generation fresh=findGeneration(item.id);
            if(!dialog.isShowing()||fresh==null||!visibleGeneration(fresh)||covered(fresh)||!"done".equals(fresh.state))return;
            Bitmap frame=videoPreview(fresh);
            if(currentId.equals(fresh.id)&&!revealed)image.setImageBitmap(frame);
            if(dragTarget.equals(fresh.id))incoming.setImageBitmap(frame);
        });}
        void prefetchVideoPreviews(){
            java.util.Set<String> near=new java.util.LinkedHashSet<>();near.add(currentId);
            for(int axis:new int[]{1,2})for(int direction:new int[]{-1,1})near.add(adjacent(axis,direction));
            for(String id:near){Generation item=findGeneration(id);if(item!=null&&visibleGeneration(item)&&!covered(item)&&"done".equals(item.state)&&"video".equals(item.mediaKind))videoPreview(item);}
        }
        void prepareIncoming(String id){
            Generation target=findGeneration(id);if(target==null||!visibleGeneration(target)){incoming.setVisibility(View.GONE);return;}
            if(target.nsfw&&!privateCoversEnabled)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            showPreview(incoming,target);incoming.setAlpha(0f);slideExtent=calculatedExtent(dragAxis);
            incoming.setVisibility(View.VISIBLE);
        }
        void dragPage(float offset){
            String target=adjacent(dragAxis,offset<0?1:-1);
            if(!target.equals(dragTarget)){dragTarget=target;slideExtent=0;if(!target.isEmpty())prepareIncoming(target);else incoming.setVisibility(View.GONE);}
            float size=extent(dragAxis);dragOffset=Math.max(-size,Math.min(size,offset));
            if(target.isEmpty())dragOffset*=.18f;
            View outgoing=outgoingMedia();translate(outgoing,dragAxis,dragOffset);
            float progress=target.isEmpty()?0f:swipeProgress();outgoing.setAlpha(1f-progress);incoming.setAlpha(progress);
            if(!target.isEmpty())translate(incoming,dragAxis,dragOffset+(offset<0?size:-size));
        }
        void finishDrag(){
            if(dragTarget.isEmpty()||Math.abs(dragOffset)<Math.min(dp(64),extent(dragAxis)*.18f)){settleBack();return;}
            completeSlide(dragTarget,dragAxis,dragOffset<0?1:-1);
        }
        void animateTo(String id,int axis,int direction){
            if(sliding||id.equals(currentId))return;cancelMotion();freezeForPaging();dragAxis=axis;dragTarget=id;prepareIncoming(id);
            translate(incoming,axis,extent(axis)*direction);completeSlide(id,axis,direction);
        }
        void completeSlide(String id,int axis,int direction){
            if(!UiMotion.enabled()){select(id);return;}
            sliding=true;int token=++motionToken;float end=-extent(axis)*direction;
            android.view.animation.Interpolator easing=new android.view.animation.DecelerateInterpolator(1.5f);
            long duration=Math.max(90,Math.round(240*(1f-swipeProgress())));
            outgoingMedia().animate().translationX(axis==2?end:0).translationY(axis==1?end:0).alpha(0f).setDuration(duration).setInterpolator(easing).start();
            incoming.animate().translationX(0).translationY(0).alpha(1f).setDuration(duration).setInterpolator(easing).withEndAction(()->{
                if(token!=motionToken||!dialog.isShowing())return;
                Generation target=findGeneration(id);if(target==null||!visibleGeneration(target)){cancelMotion();refresh();return;}
                if(!familyId(target).equals(rootId)){rememberCover();rootId=familyId(target);}select(id);
            }).start();
        }
        void settleBack(){
            if(!UiMotion.enabled()){cancelMotion();refresh();return;}
            if(dragAxis==0)return;sliding=true;int token=++motionToken;float end=dragOffset<0?extent(dragAxis):-extent(dragAxis);
            android.view.animation.Interpolator easing=new android.view.animation.DecelerateInterpolator(1.5f);
            incoming.animate().translationX(dragAxis==2?end:0).translationY(dragAxis==1?end:0).alpha(0f).setDuration(180).setInterpolator(easing).start();
            outgoingMedia().animate().translationX(0).translationY(0).alpha(1f).setDuration(180).setInterpolator(easing).withEndAction(()->{if(token==motionToken){cancelMotion();refresh();}}).start();
        }
        void cancelMotion(){
            motionToken++;image.animate().withEndAction(null).cancel();video.animate().withEndAction(null).cancel();incoming.animate().withEndAction(null).cancel();
            translate(image,0,0);translate(video,0,0);translate(incoming,0,0);
            image.setAlpha(video.isLoaded(currentId)&&video.hasFrame()?0f:1f);video.setAlpha(video.hasFrame()?1f:0f);incoming.setAlpha(0f);incoming.setVisibility(View.GONE);incoming.setImageDrawable(null);
            sliding=false;draggingVideo=false;dragAxis=0;dragOffset=0;slideExtent=0;dragTarget="";
        }

        void move(int direction) {
            for (int i = 0; i < items.size(); i++) if (items.get(i).id.equals(currentId)) {
                int target = i + direction; if (target >= 0 && target < items.size()) animateTo(items.get(target).id,1,direction); return;
            }
        }

        void select(String id) {
            cancelMotion();saveEditDraft(); conceal(); stopVideo();Generation target=findGeneration(id);if(target!=null&&!rootId.equals(familyId(target))){rememberCover();rootId=familyId(target);} boolean wasVideo=editingVideo();currentId = id;extending=false;extensionRange=new JSONObject();if(wasVideo!=editingVideo())animationEditor=editingVideo();bindDraft();status.setText("");
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(instruction.getWindowToken(), 0);
            refresh();
        }

        void refresh() {
            if (!dialog.isShowing()) return;
            if(sliding||dragAxis!=0)return;
            cancelMotion(); items = prompterContext?trialItems():versions(rootId);
            Generation recovered=restoredInputs.get(currentId);if(recovered!=null&&!items.contains(recovered))items.add(0,recovered);
            items.removeIf(item -> !visibleGeneration(item)||!prompterContext&&item.temporary);
            if (items.isEmpty()) { dialog.dismiss(); return; }
            Generation item = findGeneration(currentId);
            if (item == null || !visibleGeneration(item)) {saveEditDraft();boolean wasVideo=editingVideo();currentId=items.get(0).id;item=items.get(0);if(wasVideo!=editingVideo())animationEditor=editingVideo();bindDraft();}
            references.refresh();updateViewerLoras(this);
            if(prompterContext){FlowDesign.symbol(like,item.temporary?"heart":"heart-filled",item.temporary?UiLanguage.t("Salvar teste no acervo"):UiLanguage.t("Teste salvo"));like.setEnabled(item.temporary&&"done".equals(item.state));}
            int index = items.indexOf(item);updateVersionRail(index);
            boolean privatePreview=false;for(Generation member:items)privatePreview|=member.nsfw;
            if ((privatePreview && (!privateCoversEnabled||revealed))||references.exposedPrivate()) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            else dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            boolean pending = "queued".equals(item.state) || "running".equals(item.state);
            progress.setVisibility(!pending&&editUploads.contains(item.id) ? View.VISIBLE : View.GONE);
            boolean isVideo = "video".equals(item.mediaKind);
            if(!revealed)showPreview(image,item);prefetchVideoPreviews();
            image.setContentDescription(pending?progressDisplay(item).title+". "+progressDisplay(item).detail+UiLanguage.t(". Deslize para navegar."):"error".equals(item.state)?UiLanguage.t("Geração falhou. ")+item.error:(isVideo?UiLanguage.t("Vídeo"):UiLanguage.t("Imagem"))+UiLanguage.t(". Deslize verticalmente para versões, horizontalmente para outro conjunto."));
            boolean playable=isVideo&&"done".equals(item.state)&&visibleGeneration(item)&&(!covered(item)||revealed);
            if(playable)playVideo();else stopVideo();
            editor.setVisibility(View.VISIBLE);
            footer.setVisibility(View.VISIBLE);
            delete.setContentDescription(isVideo?UiLanguage.t("Mais opções do vídeo"):UiLanguage.t("Mais opções da imagem"));
            String origin = item.parentId.isEmpty() ? "" : findGeneration(item.parentId) == null ? "Origem apagada · " : "De " + versionName(findGeneration(item.parentId)) + " · ";
            caption.setText(pending ? (isVideo ? item.parentId.isEmpty()?UiLanguage.t("Gerando vídeo…"):UiLanguage.t("Editando vídeo…") : UiLanguage.t("Gerando edit…")) : "error".equals(item.state) ? item.error :
                    origin + (covered(item) ? "" : item.prompt));
            caption.setVisibility(View.GONE);
            reuse.setVisibility(item.prompt==null||item.prompt.trim().isEmpty()?View.GONE:View.VISIBLE);
            delete.setEnabled(true);
            send.setContentDescription(extending?UiLanguage.t("Enviar extensão de vídeo"):isVideo?UiLanguage.t("Enviar edição de vídeo"):UiLanguage.t("Enviar geração"));
            send.setEnabled("done".equals(item.state)&&(isVideo?videoFile(item.id):imageFile(item.id)).exists()&&!editUploads.contains(item.id)&&!videoSubmitting);
        }

        void reveal() {
            Generation item = findGeneration(currentId);
            if (item == null || !covered(item) || hideAllNsfw || !"done".equals(item.state)) return;
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            revealed = true;
            if ("video".equals(item.mediaKind)) playVideo();
            else image.setImageBitmap(viewerBitmap(imageFile(item.id)));
        }

        void playVideo() {
            Generation item=findGeneration(currentId);
            if(item==null||!"video".equals(item.mediaKind)||!"done".equals(item.state)||!visibleGeneration(item)||(covered(item)&&!revealed))return;
            if(item.nsfw)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            video.play(videoFile(item.id),item.id);
            image.setAlpha(video.hasFrame()?0f:1f);
        }

        void stopVideo() {video.stop();image.setAlpha(1f);image.setVisibility(View.VISIBLE);}

        void freezeForPaging(){
            if(revealed)conceal();
            video.freezeForGesture();draggingVideo=video.isLoaded(currentId)&&video.hasFrame();
            image.setAlpha(draggingVideo?0f:1f);image.setVisibility(View.VISIBLE);
        }

        void conceal() {
            if (!revealed) return;
            Generation item = findGeneration(currentId);
            image.setImageBitmap(item == null ? null : coverBitmap(item)); stopVideo(); revealed = false;
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    private JSONObject getJson(String address) throws Exception {
        return requestJson("GET", address, null);
    }

    private JSONObject requestJson(String method, String address, JSONObject body) throws Exception {
        if("POST".equals(method)&&address.endsWith("/prompt")&&body!=null&&body.optJSONObject("prompt")!=null){
            PrivateConditioning.apply(body.getJSONObject("prompt"),!hideAllNsfw);
            SfwPromptFilter.apply(body.getJSONObject("prompt"),hideAllNsfw);
            if(hideAllNsfw)LoraPrivacy.enforce(body.getJSONObject("prompt"));
        }
        byte[] bytes = requestBytes(method, address, body, 10_000_000);
        return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    }

    private byte[] requestBytes(String method, String address, JSONObject body, int limit) throws Exception {
        HttpURLConnection connection = DesktopConnection.open(this,address);
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(15000);
        try {
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) { output.write(data); }
            }
            int code = connection.getResponseCode();
            if(code>=300&&code<400)throw new IOException(UiLanguage.t("Redirecionamento do servidor recusado."));
            InputStream input = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            byte[] content = read(input, limit);
            if (code >= 400) {
                String message = new String(content, StandardCharsets.UTF_8);
                throw new IllegalStateException(UiLanguage.t("HTTP ") + code + ": " + message.substring(0, Math.min(message.length(), 500)));
            }
            return content;
        } finally { connection.disconnect(); }
    }

    private byte[] read(InputStream input, int limit) throws Exception {
        if (input == null) throw new IllegalStateException(UiLanguage.t("Resposta vazia."));
        try (InputStream stream = input; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = stream.read(chunk)) != -1) {
                if (buffer.size() + count > limit) throw new IllegalStateException(UiLanguage.t("Arquivo grande demais."));
                buffer.write(chunk, 0, count);
            }
            return buffer.toByteArray();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state){if(!vaultUiReady){super.onSaveInstanceState(state);return;}saveDraft();if(versionViewer!=null)versionViewer.saveEditDraft();if(helpCenter!=null)helpCenter.save(state);state.putInt("selectedTab",selectedTab);state.putInt("fragmentReturnTab",fragmentReturnTab);state.putString("activeProjectId",activeProjectId);super.onSaveInstanceState(state);}

    @Override protected void onDestroy() {
        if(referenceStudio!=null)referenceStudio.audio.close();
        if(lipsyncSheet!=null)lipsyncSheet.dialog.dismiss();pickingLipsync=null;
        if(helpCenter!=null)helpCenter.dispose();
        if(motionLight!=null)motionLight.stop();
        if(serverMonitor!=null)serverMonitor.close();
        polling = false;
        stopProgressSocket();stopFeedPlayback();
        if(PrompterCleanupService.activity.get()==this)PrompterCleanupService.activity.clear();
        handler.removeCallbacksAndMessages(null);
        worker.shutdownNow();
        if(referenceJobs!=null)referenceJobs.close();
        if(referenceStudio!=null)referenceStudio.extractor.close();
        super.onDestroy();
    }
}
