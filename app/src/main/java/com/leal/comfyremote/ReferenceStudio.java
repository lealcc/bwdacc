package com.leal.comfyremote;

import android.app.*;
import android.graphics.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** One visual language and one editor for people, styles and places. */
final class ReferenceStudio {
    interface Pick {void accept(List<ReferencePreset.Picture> images);}
    interface Host {
        String tag(ReferencePreset preset);void insert(String tag);String address();boolean nsfw();
        Bitmap preview(ReferencePreset preset,ReferencePreset.Picture image,boolean reveal);
        void choose(boolean library,int remaining,Pick callback);
        void composition(ReferencePreset preset);
        default void audio(ReferencePreset preset){}
    }
    final Activity activity;final ReferencePreset.Store store;final ReferenceJobs jobs;final Host host;
    final ReferenceExtractor extractor;
    final AudioTools audio;
    Dialog manager,editor;Runnable renderManager=()->{},refreshJobs=()->{};
    static final int INK=FlowDesign.INK,MUTED=FlowDesign.MUTED,SURFACE=FlowDesign.SURFACE,CONTROL=FlowDesign.CONTROL;
    ReferenceStudio(Activity a,ReferencePreset.Store store,ReferenceJobs jobs,Host host){activity=a;this.store=store;this.jobs=jobs;this.host=host;audio=new AudioTools(a,store,jobs.worker,host::nsfw);extractor=new ReferenceExtractor(this);jobs.changed=()->{refreshJobs.run();};}
    int dp(float n){return FlowDesign.dp(activity,n);}
    LinearLayout column(){LinearLayout v=new LinearLayout(activity);v.setOrientation(1);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(activity);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    void gap(LinearLayout parent,int n){parent.addView(new View(activity),new LinearLayout.LayoutParams(1,dp(n)));}
    TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Branding.body(activity,bold?650:400));return t;}
    Button button(String value,String icon){FlowDesign.IconButton b=new FlowDesign.IconButton(activity);b.setText(value);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(INK);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setStateListAnimator(null);b.setPadding(dp(14),0,dp(14),0);b.setBackground(FlowDesign.shape(activity,CONTROL,22,false));if(icon!=null)FlowDesign.labelIcon(b,icon);return b;}
    Button icon(String symbol,String label){Button b=button("",null);FlowDesign.symbol(b,symbol,label);return b;}
    EditText field(String hint,int lines){EditText e=new EditText(activity);e.setTextSize(16);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setHint(hint);e.setGravity(Gravity.TOP|Gravity.START);e.setPadding(dp(14),dp(14),dp(14),dp(14));e.setBackground(FlowDesign.shape(activity,CONTROL,18,false));e.setMinLines(lines);e.setMaxLines(lines==1?1:6);e.setSingleLine(lines==1);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|(lines>1?android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE:android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));return e;}
    LinearLayout card(LinearLayout parent,String title,String detail){gap(parent,18);LinearLayout c=column();c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackground(FlowDesign.shape(activity,0xFF1B1C1E,24,false));c.addView(text(title,16,INK,true));if(!detail.isEmpty()){gap(c,6);c.addView(text(detail,12,MUTED,false));}gap(c,12);parent.addView(c);return c;}
    Dialog sheet(LinearLayout body,float height){
        Dialog d=new Dialog(activity);d.setContentView(body);UiMotion.dialog(d);d.show();Window w=d.getWindow();w.setBackgroundDrawable(FlowDesign.shape(activity,SURFACE,28,true));w.setGravity(Gravity.BOTTOM);w.setLayout(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*height));w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);body.setClipToOutline(true);
        final int bottom=body.getPaddingBottom();body.getViewTreeObserver().addOnGlobalLayoutListener(()->{
            Rect visible=new Rect();body.getWindowVisibleDisplayFrame(visible);int[] position=new int[2];body.getLocationOnScreen(position);
            int overlap=Math.max(0,position[1]+body.getHeight()-visible.bottom),padding=bottom+overlap;
            if(body.getPaddingBottom()!=padding)body.setPadding(body.getPaddingLeft(),body.getPaddingTop(),body.getPaddingRight(),padding);
        });return d;
    }
    LinearLayout page(String title,Runnable close){LinearLayout page=column();page.setPadding(dp(16),dp(12),dp(16),dp(16));page.setBackground(FlowDesign.shape(activity,SURFACE,28,true));LinearLayout header=row();header.addView(text(title,22,INK,true),new LinearLayout.LayoutParams(0,dp(48),1));Button x=icon("close",UiLanguage.t("Fechar ")+title.toLowerCase(Locale.ROOT));header.addView(x,new LinearLayout.LayoutParams(dp(44),dp(44)));x.setOnClickListener(v->close.run());page.addView(header);gap(page,12);return page;}
    boolean managingFullArea;
    View library(int first){managingFullArea=true;return new FragmentLibrary(this,first,true).view;}
    void manage(int first){managingFullArea=false;manager=sheet(new FragmentLibrary(this,first,false).view,.86f);}
    ImageView image(ReferencePreset p,ReferencePreset.Picture picture,int size){ImageView v=new ImageView(activity);v.setScaleType(ImageView.ScaleType.CENTER_CROP);v.setBackground(FlowDesign.shape(activity,CONTROL,18,false));v.setClipToOutline(true);Bitmap bitmap=host.preview(p,picture,false);if(bitmap!=null)v.setImageBitmap(bitmap);else {v.setScaleType(ImageView.ScaleType.CENTER);v.setImageDrawable(FlowDesign.icon(activity,p.icon(),28));}v.setContentDescription(p.name.isEmpty()?UiLanguage.t("Imagem de referência"):UiLanguage.t("Imagem de ")+p.name);return v;}
    void permission(LinearLayout parent,String title,String detail,String symbol,boolean checked,java.util.function.Consumer<Boolean> change){
        LinearLayout line=row();line.setPadding(0,dp(8),0,dp(8));line.setMinimumHeight(dp(64));
        ImageView glyph=new ImageView(activity);glyph.setImageDrawable(FlowDesign.icon(activity,symbol,22));glyph.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);line.addView(glyph,new LinearLayout.LayoutParams(dp(32),dp(32)));
        LinearLayout label=column();label.setPadding(dp(10),0,dp(12),0);label.addView(text(title,15,INK,true));gap(label,3);label.addView(text(detail,12,MUTED,false));line.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=new Switch(activity);toggle.setContentDescription(UiLanguage.t("Usar ")+title.toLowerCase(Locale.ROOT));toggle.setChecked(checked);toggle.setShowText(false);toggle.setSwitchMinWidth(dp(42));
        toggle.setThumbTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xFFE4ECE8,0xFF96999B}));
        toggle.setTrackTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xFF596F64,0xFF3C3E41}));
        line.addView(toggle,new LinearLayout.LayoutParams(dp(48),dp(48)));label.setOnClickListener(v->toggle.setChecked(!toggle.isChecked()));toggle.setOnCheckedChangeListener((v,on)->{change.accept(on);refreshJobs.run();});parent.addView(line);
    }
    void use(ReferencePreset preset){if(preset.control()){edit(preset);return;}if(preset.hidden()&&!host.nsfw()){toast(UiLanguage.t("Ative o modo privado para usar esta referência."));return;}if(preset.audio())host.audio(preset);else if(preset.kind.equals("composition"))host.composition(preset);else host.insert(host.tag(preset));}
    void editComposition(ReferencePreset original){
        ReferencePreset draft=original.copy();draft.useImages=false;draft.useRefmod=false;draft.useLoras=false;draft.useText=false;
        final boolean exists=store.get(draft.id)!=null;
        LinearLayout page=page(UiLanguage.t("Composição"),()->editor.dismiss());
        EditText name=field(UiLanguage.t("Nome da composição"),1);name.setText(draft.name);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});page.addView(name,new LinearLayout.LayoutParams(-1,dp(54)));gap(page,16);
        ImageView preview=image(draft,draft.coverPicture(),180);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);page.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        TextView count=text("",14,MUTED,false);count.setGravity(Gravity.CENTER);page.addView(count,new LinearLayout.LayoutParams(-1,dp(40)));
        Runnable render=()->{JSONArray boxes=draft.layout.optJSONArray("boxes");count.setText((boxes==null?0:boxes.length())+" "+UiLanguage.t("boxes editáveis"));};render.run();
        Button extract=button(UiLanguage.t("Extrair de imagem"),"scan");page.addView(extract,new LinearLayout.LayoutParams(-1,dp(48)));gap(page,8);
        extract.setOnClickListener(v->{PopupMenu menu=new OrganicPopupMenu(activity,extract);for(boolean library:new boolean[]{false,true})menu.getMenu().add(library?UiLanguage.t("Biblioteca"):UiLanguage.t("Imagens")).setOnMenuItemClickListener(it->{host.choose(library,1,images->{if(images.isEmpty())return;editor.dismiss();extractor.open(images.get(0),true);});return true;});MenuIcons.show(activity,menu);});
        Button boxes=button(UiLanguage.t("Editar boxes"),"boxes");page.addView(boxes,new LinearLayout.LayoutParams(-1,dp(48)));gap(page,12);
        boxes.setOnClickListener(v->{Bitmap background=draft.hidden()?null:host.preview(draft,draft.coverPicture(),false);BoxEditor.show(activity,draft.layout,draft.layout.optDouble("aspect",1),background,value->{draft.layout=value;render.run();});});
        Button done=button(exists||managingFullArea?UiLanguage.t("Salvar"):UiLanguage.t("Salvar e usar"),"check");page.addView(done,new LinearLayout.LayoutParams(-1,dp(52)));
        done.setOnClickListener(v->{try{
            draft.name=name.getText().toString().trim();if(draft.name.isEmpty()){name.setError(UiLanguage.t("Digite um nome."));return;}
            for(ReferencePreset p:store.all())if(!p.id.equals(draft.id)&&p.kind.equals("composition")&&p.name.equalsIgnoreCase(draft.name)){name.setError(UiLanguage.t("Nome já existe."));return;}
            draft.layout.put("enabled",true);if(BoxLayout.regions(draft.layout).length()==0){toast(UiLanguage.t("Adicione pelo menos um box."));return;}
            store.save(draft);editor.dismiss();if(!exists&&!managingFullArea){if(manager!=null)manager.dismiss();use(draft);}
        }catch(Exception error){toast(error.getMessage());}});
        editor=sheet(page,.86f);editor.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);editor.setOnDismissListener(d->{renderManager.run();});
    }
    void edit(ReferencePreset original){
        if(original.audio()){new AudioFragmentEditor(this,original);return;}
        if(original.control()){new ControlFragmentEditor(this,original).show();return;}
        if(original.kind.equals("composition")){editComposition(original);return;}
        ReferencePreset draft=original.copy();final boolean exists=store.get(draft.id)!=null;LinearLayout page=page((exists?UiLanguage.t("Editar "):UiLanguage.t("Novo "))+draft.label(),()->editor.dismiss());
        ScrollView scroll=new ScrollView(activity);scroll.setVerticalScrollBarEnabled(false);scroll.setFillViewport(false);LinearLayout form=column();scroll.addView(form);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));gap(page,12);
        EditText name=field(draft.kind.equals("character")?UiLanguage.t("Nome do personagem"):draft.kind.equals("style")?UiLanguage.t("Nome do estilo"):draft.kind.equals("object")?UiLanguage.t("Nome do objeto"):UiLanguage.t("Nome do local"),1);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});name.setText(draft.name);name.setContentDescription(UiLanguage.t("Nome da referência"));form.addView(name,new LinearLayout.LayoutParams(-1,dp(54)));
        LinearLayout usage=card(form,UiLanguage.t("Usar na geração"),UiLanguage.t("Combine as opções que permitir. Cada workflow usa apenas o que suporta."));
        permission(usage,UiLanguage.t("Texto"),UiLanguage.t("Sua descrição e o complemento visual."),"edit",draft.useText,on->draft.useText=on);
        permission(usage,UiLanguage.t("Imagens"),UiLanguage.t("Em workflows com referências visuais."),"image",draft.useImages,on->draft.useImages=on);
        permission(usage,UiLanguage.t("LoRAs"),UiLanguage.t("Somente os compatíveis com o modelo."),"layers",draft.useLoras,on->draft.useLoras=on);
        permission(usage,UiLanguage.t("RefMod"),UiLanguage.t("Somente MiniMax H3 Ref2VA."),"video",draft.useRefmod,on->draft.useRefmod=on);
        LinearLayout visual=card(form,UiLanguage.t("Imagens"),UiLanguage.t("A capa entra primeiro como referência. Outras imagens preenchem os espaços disponíveis no workflow."));
        LinearLayout pictures=row();HorizontalScrollView pictureScroll=new HorizontalScrollView(activity);pictureScroll.setHorizontalScrollBarEnabled(false);pictureScroll.addView(pictures);visual.addView(pictureScroll,new LinearLayout.LayoutParams(-1,dp(92)));LinearLayout addRow=row();gap(visual,12);Button add=button(UiLanguage.t("Adicionar"),"plus");addRow.addView(add,new LinearLayout.LayoutParams(0,dp(44),1));visual.addView(addRow);
        Switch privacy=new Switch(activity);privacy.setText(UiLanguage.t("Proteger imagens"));privacy.setTextSize(14);privacy.setTextColor(INK);privacy.setChecked(draft.privateCover);gap(visual,12);visual.addView(privacy);
        LinearLayout prose=card(form,UiLanguage.t("Sua descrição"),UiLanguage.t("Sua descrição permanece separada da descrição visual automática."));EditText description=field(draft.kind.equals("character")?UiLanguage.t("Aparência e características constantes…"):draft.kind.equals("style")?UiLanguage.t("Luz, cores, técnica, atmosfera…"):UiLanguage.t("Ambiente, elementos e detalhes…"),3);description.setText(draft.text);description.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(10000)});description.setContentDescription(UiLanguage.t("Descrição da referência"));prose.addView(description);
        LinearLayout auto=column();gap(prose,12);Button expand=button(UiLanguage.t("Descrição visual automática"),"down");prose.addView(expand,new LinearLayout.LayoutParams(-1,dp(44)));prose.addView(auto);auto.setVisibility(View.GONE);expand.setOnClickListener(v->auto.setVisibility(auto.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        Switch useGhost=new Switch(activity);useGhost.setText(UiLanguage.t("Complementar texto com a imagem"));useGhost.setTextColor(INK);useGhost.setTextSize(13);useGhost.setChecked(draft.useGhost);gap(auto,12);auto.addView(useGhost);TextView ghost=text("",13,MUTED,false);gap(auto,12);auto.addView(ghost);Button generate=button(UiLanguage.t("Atualizar descrição"),"refresh");gap(auto,12);auto.addView(generate,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout adapters=card(form,UiLanguage.t("LoRAs por modelo"),UiLanguage.t("Aplicados pela tag apenas quando o modelo é compatível. Pesos são do modelo."));LinearLayout loraList=column();adapters.addView(loraList);Button chooseLoras=button(UiLanguage.t("Vincular LoRAs"),"layers");adapters.addView(chooseLoras,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout refmod=card(form,UiLanguage.t("RefMod"),UiLanguage.t("Salve as imagens como referência visual reutilizável para MiniMax H3 Ref2VA."));TextView modStatus=text("",13,MUTED,false);refmod.addView(modStatus);gap(refmod,12);Button createMod=button(UiLanguage.t("Gerar RefMod H3"),"layers");refmod.addView(createMod,new LinearLayout.LayoutParams(-1,dp(44)));gap(form,18);
        final Runnable[] refreshPictures={null},refreshLoras={null};
        refreshPictures[0]=()->{
            pictures.removeAllViews();for(ReferencePreset.Picture picture:draft.images){FrameLayout tile=new FrameLayout(activity);ImageView view=image(draft,picture,88);tile.addView(view,new FrameLayout.LayoutParams(-1,-1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(88),dp(88));lp.rightMargin=dp(8);pictures.addView(tile,lp);
                if(draft.coverPicture()==picture){TextView badge=text(UiLanguage.t("Capa"),10,INK,true);badge.setPadding(dp(6),dp(3),dp(6),dp(3));badge.setBackground(FlowDesign.shape(activity,0xCC161719,9,false));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);bp.setMargins(dp(5),0,0,dp(5));tile.addView(badge,bp);}
                view.setOnClickListener(v->{PopupMenu menu=new OrganicPopupMenu(activity,view);menu.getMenu().add(UiLanguage.t("Usar como capa")).setOnMenuItemClickListener(it->{draft.cover=picture.file;refreshPictures[0].run();return true;});menu.getMenu().add(UiLanguage.t("Selecionar objeto")).setOnMenuItemClickListener(it->{extractor.open(new ReferencePreset.Picture(picture.file,picture.source,draft.hidden()),false);return true;});menu.getMenu().add(UiLanguage.t("Extrair composição")).setOnMenuItemClickListener(it->{extractor.open(new ReferencePreset.Picture(picture.file,picture.source,draft.hidden()),true);return true;});menu.getMenu().add(UiLanguage.t("Remover imagem")).setOnMenuItemClickListener(it->{draft.images.remove(picture);if(draft.cover.equals(picture.file))draft.cover="";refreshPictures[0].run();return true;});MenuIcons.show(activity,menu);});
                view.setOnLongClickListener(v->{view.setImageBitmap(host.preview(draft,picture,true));return true;});view.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)view.setImageBitmap(host.preview(draft,picture,false));return false;});
            }
            if(draft.images.isEmpty()){ImageView empty=image(draft,null,88);pictures.addView(empty,new LinearLayout.LayoutParams(dp(88),dp(88)));TextView note=text(UiLanguage.t("Texto e LoRA também funcionam sem imagem."),13,MUTED,false);note.setPadding(dp(14),0,0,0);pictures.addView(note,new LinearLayout.LayoutParams(dp(180),-2));}
            add.setEnabled(draft.images.size()<3);boolean locked=false;for(ReferencePreset.Picture p:draft.images)locked|=p.hidden;privacy.setEnabled(!locked);if(locked){privacy.setChecked(true);draft.privateCover=true;}if(draft.hidden()&&editor!=null)editor.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);refreshJobs.run();
        };
        privacy.setOnCheckedChangeListener((b,on)->{draft.privateCover=on;refreshPictures[0].run();});
        add.setOnClickListener(v->{PopupMenu menu=new OrganicPopupMenu(activity,add);for(boolean library:new boolean[]{false,true})menu.getMenu().add(library?UiLanguage.t("Biblioteca"):UiLanguage.t("Imagens")).setOnMenuItemClickListener(it->{host.choose(library,3-draft.images.size(),images->{if(editor==null||!editor.isShowing())return;for(ReferencePreset.Picture p:images)if(draft.images.size()<3)draft.images.add(p);refreshPictures[0].run();});return true;});MenuIcons.show(activity,menu);});
        refreshLoras[0]=()->{loraList.removeAllViews();for(int i=0;i<draft.loras.length();i++){JSONObject entry=draft.loras.optJSONObject(i);if(entry==null)continue;LinearLayout item=column();String path=entry.optString("lora");if(!host.nsfw()&&LoraPrivacy.restricted(path))continue;TextView label=text(LoraPrivacy.display(path),14,INK,false);label.setMaxLines(2);label.setEllipsize(TextUtils.TruncateAt.END);item.addView(label);LinearLayout controls=row();controls.addView(text(ReferenceBindings.familyLabel(entry.optString("family")),12,MUTED,false),new LinearLayout.LayoutParams(0,dp(48),1));controls.addView(stepper(entry));Button remove=icon("close",UiLanguage.t("Remover LoRA ")+path);controls.addView(remove,new LinearLayout.LayoutParams(dp(42),dp(44)));remove.setOnClickListener(v->{JSONArray next=new JSONArray();for(int at=0;at<draft.loras.length();at++)if(draft.loras.optJSONObject(at)!=entry)next.put(draft.loras.optJSONObject(at));draft.loras=next;refreshLoras[0].run();});item.addView(controls);loraList.addView(item);gap(loraList,12);}};
        chooseLoras.setOnClickListener(v->chooseLoras(draft,refreshLoras[0]));refreshLoras[0].run();
        final java.util.concurrent.Callable<ReferencePreset> save=()->{
            String title=name.getText().toString().trim();if(title.isEmpty()){name.setError(UiLanguage.t("Digite um nome."));return null;}
            for(ReferencePreset p:store.all())if(!p.id.equals(draft.id)&&p.kind.equals(draft.kind)&&p.name.equalsIgnoreCase(title)){name.setError(UiLanguage.t("Nome já existe."));return null;}
            ReferencePreset latest=store.get(draft.id),p=draft.copy();p.name=title;p.text=description.getText().toString().trim();p.useGhost=useGhost.isChecked();p.privateCover=privacy.isChecked();p.usageVersion=1;
            if(p.text.isEmpty()&&p.images.isEmpty()&&p.loras.length()==0){description.setError(UiLanguage.t("Adicione texto, imagem ou LoRA."));return null;}
            if(latest!=null){p.jobs=latest.jobs;p.ghost=latest.ghost;p.ghostSource=latest.ghostSource;p.refmod=latest.refmod;p.refmodSource=latest.refmodSource;p.refmodServer=latest.refmodServer;}
            if(p.coverPicture()==null||!p.coverPicture().file.equals(p.ghostSource)){p.ghost="";p.ghostSource="";}
            store.save(p);return p;
        };
        refreshJobs=()->{ReferencePreset p=store.get(draft.id);if(p==null)p=draft;JSONObject caption=p.jobs.optJSONObject("ghost"),mod=p.jobs.optJSONObject("refmod");boolean activeCaption=active(caption),activeMod=active(mod);
            String ghostText=!p.ghost.isEmpty()?p.ghost:caption==null?"Gerada automaticamente ao salvar uma imagem, quando o servidor oferece um modelo visual local.":caption.optString("message");
            if(!p.ghost.isEmpty()&&caption!=null&&"error".equals(caption.optString("state")))ghostText+="\n\n"+caption.optString("message");
            if(draft.coverPicture()!=null&&!p.ghostSource.isEmpty()&&!draft.coverPicture().file.equals(p.ghostSource))ghostText="Capa alterada. A descrição visual será atualizada ao salvar.";ghost.setText(ghostText);
            generate.setEnabled(!activeCaption&&!draft.images.isEmpty());generate.setText(activeCaption?UiLanguage.t("Gerando descrição…"):UiLanguage.t("Atualizar descrição"));
            modStatus.setText(activeMod?mod.optString("message"):mod!=null&&"error".equals(mod.optString("state"))?mod.optString("message"):!p.refmod.isEmpty()?(p.refmodSource.equals(draft.signature())?(draft.useRefmod?UiLanguage.t("Pronto para usar pela tag no H3 Ref2VA."):UiLanguage.t("RefMod pronto. Uso na geração desativado.")):UiLanguage.t("Imagens alteradas. Gere um novo RefMod.")):UiLanguage.t("Usa todas as imagens desta referência."));
            createMod.setEnabled(!activeMod&&!draft.images.isEmpty());createMod.setText(activeMod?UiLanguage.t("Gerando RefMod…"):p.refmod.isEmpty()?UiLanguage.t("Gerar RefMod H3"):UiLanguage.t("Atualizar RefMod H3"));
        };
        generate.setOnClickListener(v->{try{ReferencePreset p=save.call();if(p!=null)jobs.start(p,"ghost",host.address());}catch(Exception e){toast(e.getMessage());}});
        createMod.setOnClickListener(v->{try{ReferencePreset p=save.call();if(p!=null)jobs.start(p,"refmod",host.address());}catch(Exception e){toast(e.getMessage());}});
        Button done=button(exists||managingFullArea?UiLanguage.t("Salvar"):UiLanguage.t("Salvar e usar"),null);done.setTextColor(0xFF121314);done.setBackground(FlowDesign.shape(activity,0xFFE4ECE8,24,false));page.addView(done,new LinearLayout.LayoutParams(-1,dp(52)));
        done.setOnClickListener(v->{try{ReferencePreset p=save.call();if(p==null)return;editor.dismiss();if(!exists&&!managingFullArea){if(manager!=null)manager.dismiss();host.insert(host.tag(p));}jobs.describeIfNeeded(p,host.address());}catch(Exception e){toast(e.getMessage());}});
        editor=sheet(page,.90f);editor.setOnDismissListener(d->{refreshJobs=()->{};renderManager.run();});refreshPictures[0].run();refreshJobs.run();
    }
    void editExtracted(ReferencePreset preset){
        final Dialog parent=editor;final Runnable parentRefresh=refreshJobs;
        if(parent==null||!parent.isShowing()){edit(preset);return;}
        parent.hide();edit(preset);
        editor.setOnDismissListener(d->{editor=parent;refreshJobs=parentRefresh;parent.show();parentRefresh.run();renderManager.run();});
    }
    static boolean active(JSONObject job){return job!=null&&Arrays.asList("preparing","submitting","queued").contains(job.optString("state"));}
    View stepper(JSONObject entry){LinearLayout row=row();Button minus=icon("minus",UiLanguage.t("Diminuir peso")),plus=icon("plus",UiLanguage.t("Aumentar peso"));TextView value=text("",14,INK,true);value.setGravity(Gravity.CENTER);Runnable update=()->value.setText(String.format(Locale.ROOT,UiLanguage.t("%.1f"),entry.optDouble("strength",1)));update.run();row.addView(minus,new LinearLayout.LayoutParams(dp(40),dp(40)));row.addView(value,new LinearLayout.LayoutParams(dp(40),dp(40)));row.addView(plus,new LinearLayout.LayoutParams(dp(40),dp(40)));minus.setOnClickListener(v->{try{entry.put("strength",Math.max(-20,Math.round((entry.optDouble("strength",1)-.1)*10)/10.0));update.run();}catch(Exception ignored){}});plus.setOnClickListener(v->{try{entry.put("strength",Math.min(20,Math.round((entry.optDouble("strength",1)+.1)*10)/10.0));update.run();}catch(Exception ignored){}});return row;}
    void chooseLoras(ReferencePreset draft,Runnable refresh){
        String address=host.address();if(address==null){toast("Conecte ao servidor em Ajustes.");return;}
        LinearLayout page=page(UiLanguage.t("Vincular LoRAs"),()->{});EditText search=field(UiLanguage.t("Buscar LoRA ou modelo"),1);page.addView(search);ScrollView scroll=new ScrollView(activity);LinearLayout list=column();scroll.addView(list);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));Button done=button(UiLanguage.t("Concluir"),null);page.addView(done,new LinearLayout.LayoutParams(-1,dp(48)));Dialog dialog=sheet(page,.74f);((Button)((LinearLayout)page.getChildAt(0)).getChildAt(1)).setOnClickListener(v->dialog.dismiss());done.setOnClickListener(v->dialog.dismiss());dialog.setOnDismissListener(d->refresh.run());list.addView(text(UiLanguage.t("Buscando modelos instalados…"),14,MUTED,false));
        jobs.worker.execute(()->{try{
            LoraPrivacy.refresh(address,url->jobs.server.json("GET",url,null));
            JSONObject info=jobs.server.json("GET",address+"/object_info/LoraLoader",null);JSONArray paths=info.getJSONObject("LoraLoader").getJSONObject("input").getJSONObject("required").getJSONArray("lora_name").getJSONArray(0);List<String> available=new ArrayList<>();for(int i=0;i<paths.length();i++)if(!ReferenceBindings.loraFamily(paths.getString(i)).isEmpty()&&(host.nsfw()||!LoraPrivacy.restricted(paths.getString(i))))available.add(paths.getString(i));
            Set<String> selected=new HashSet<>();for(int i=0;i<draft.loras.length();i++)selected.add(draft.loras.optJSONObject(i).optString("lora"));Collections.sort(available,(a,b)->{int order=Boolean.compare(selected.contains(b),selected.contains(a));return order==0?String.CASE_INSENSITIVE_ORDER.compare(a,b):order;});
            activity.runOnUiThread(()->{if(!dialog.isShowing())return;Runnable draw=()->{list.removeAllViews();String filter=search.getText().toString().toLowerCase(Locale.ROOT);for(String path:available){if(!host.nsfw()&&LoraPrivacy.restricted(path))continue;String family=ReferenceBindings.loraFamily(path);if(!LoraPrivacy.matchesSearch(path,filter)&&!ReferenceBindings.familyLabel(family).toLowerCase(Locale.ROOT).contains(filter))continue;
                JSONObject entry=null;for(int i=0;i<draft.loras.length();i++)if(path.equals(draft.loras.optJSONObject(i).optString("lora")))entry=draft.loras.optJSONObject(i);CheckBox check=new CheckBox(activity);check.setTextColor(INK);check.setTextSize(13);check.setPadding(dp(4),dp(8),dp(4),dp(8));check.setText(ReferenceBindings.familyLabel(family)+" · "+LoraPrivacy.display(path)+" · "+LoraPrivacy.label(path));check.setChecked(entry!=null);check.setOnCheckedChangeListener((b,on)->{JSONArray next=new JSONArray();for(int i=0;i<draft.loras.length();i++)if(!path.equals(draft.loras.optJSONObject(i).optString("lora")))next.put(draft.loras.optJSONObject(i));if(on)try{next.put(new JSONObject().put("family",family).put("lora",path).put("strength",1));}catch(Exception ignored){}draft.loras=next;});list.addView(check,new LinearLayout.LayoutParams(-1,-2));}if(list.getChildCount()==0)list.addView(text(UiLanguage.t("Nenhum LoRA compatível encontrado."),14,MUTED,false));};search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){draw.run();}public void afterTextChanged(Editable e){}});draw.run();});
        }catch(Exception e){activity.runOnUiThread(()->{list.removeAllViews();list.addView(text(UiLanguage.t("Falha ao listar LoRAs. Confira a conexão."),14,MUTED,false));});}});
    }
    void toast(String message){Toast.makeText(activity,message,Toast.LENGTH_LONG).show();}
}
