#include <jni.h>
#include <dlfcn.h>
#include <stdint.h>
#include <string>
#include <vector>
#include <cstring>
#include <algorithm>
#include <cctype>
#include <utility>

// Minimal libretro ABI definitions. For a general-purpose frontend, use the upstream libretro.h.
struct game_info { const char* path; const void* data; size_t size; const char* meta; };
struct system_info { const char *library_name,*library_version,*valid_extensions; bool need_fullpath,block_extract; };
struct geometry { unsigned base_width,base_height,max_width,max_height; float aspect_ratio; };
struct timing { double fps,sample_rate; };
struct av_info { geometry geo; timing time; };
static void *handle=nullptr; static bool loaded=false,multitapDevice=true; static unsigned width=0,height=0,pixfmt=0;
static std::vector<uint32_t> frame; static std::vector<int16_t> samples; static std::vector<uint8_t> rom;static std::string romPath;
static uint16_t inputs[5]={}; static std::string saveDir; static double sampleRate=32040.0,fps=60.0988;
using env_t=bool(*)(unsigned,void*); using video_t=void(*)(const void*,unsigned,unsigned,size_t);
using audio_t=void(*)(int16_t,int16_t); using audio_batch_t=size_t(*)(const int16_t*,size_t);
using poll_t=void(*)(); using state_t=int16_t(*)(unsigned,unsigned,unsigned,unsigned);
static void (*set_env)(env_t);static void (*set_video)(video_t);static void (*set_audio)(audio_t);static void (*set_audio_batch)(audio_batch_t);static void (*set_poll)(poll_t);static void (*set_state)(state_t);
static void (*init)();static void (*deinit)();static bool (*load_game)(const game_info*);static void (*unload_game)();static void (*run)();static void (*reset_core)();static void (*controller)(unsigned,unsigned);static void (*get_av)(av_info*);static void (*get_info)(system_info*);
static void* (*get_memory_data)(unsigned);static size_t (*get_memory_size)(unsigned);static size_t (*serialize_size)();static bool (*serialize)(void*,size_t);static bool (*unserialize)(const void*,size_t);
static void (*cheat_reset)()=nullptr;static void (*cheat_set)(unsigned,bool,const char*)=nullptr;
struct core_variable { const char* key; const char* value; };
static bool hasPicture=false;
static bool env(unsigned command,void *data){
 command &= ~0x10000u; // RETRO_ENVIRONMENT_EXPERIMENTAL
 switch(command){
 case 3: *static_cast<bool*>(data)=true; return true; // GET_CAN_DUPE
 case 9: case 31: *static_cast<const char**>(data)=saveDir.c_str();return true;
 case 10: {unsigned requested=*static_cast<unsigned*>(data);if(requested>2)return false;pixfmt=requested;return true;}
 case 15: {
  // Several bsnes-mercury games need a coprocessor firmware unless HLE is enabled.
  // The core applies HLE only to chips for which an implementation exists.
  auto* variable=static_cast<core_variable*>(data);
  if(!variable||!variable->key)return false;
  if(strcmp(variable->key,"bsnes_violate_accuracy")==0)variable->value="enabled";
  else if(strcmp(variable->key,"bsnes_chip_hle")==0)variable->value="HLE";
  else return false;
  return true;
 }
 case 17: *static_cast<bool*>(data)=false;return true;
 case 39: *static_cast<bool*>(data)=false;return true; // GET_INPUT_BITMASKS
 default: return false;
 }
}
static void video(const void *pixels,unsigned w,unsigned h,size_t pitch){
 if(!pixels||w==0||h==0||w>1024||h>1024)return;
 width=w;height=h;frame.resize((size_t)w*h);
 for(unsigned y=0;y<h;y++){
  const uint8_t* row=(const uint8_t*)pixels+y*pitch;
  for(unsigned x=0;x<w;x++){
   uint32_t c;
   if(pixfmt==1){uint32_t v=((const uint32_t*)row)[x];c=0xff000000u|(v&0x00ffffffu);}
   else if(pixfmt==2){uint16_t v=((const uint16_t*)row)[x];c=0xff000000u|((v&0xf800u)<<8)|((v&0x07e0u)<<5)|((v&0x001fu)<<3);}
   else{uint16_t v=((const uint16_t*)row)[x];c=0xff000000u|((v&0x7c00u)<<9)|((v&0x03e0u)<<6)|((v&0x001fu)<<3);}
   frame[(size_t)y*w+x]=c;
   if((c&0x00ffffffu)!=0)hasPicture=true;
  }
 }
}
static void audio(int16_t l,int16_t r){samples.push_back(l);samples.push_back(r);}
static size_t batch(const int16_t *p,size_t count){samples.insert(samples.end(),p,p+count*2);return count;}
static void poll(){}
// bsnes-mercury reports the four controllers on port 2 as libretro user ports 1–4.
// For those users it can pass the Multitap subclass (257), not just Joypad (1).
static int16_t state(unsigned port,unsigned device,unsigned index,unsigned id){return (device==1||device==257)&&index==0&&port<5&&id<16 ? ((inputs[port]>>id)&1):0;}
template<typename T> static bool bind(T &fn,const char* symbol){fn=reinterpret_cast<T>(dlsym(handle,symbol));return fn!=nullptr;}
static void close_core(){if(loaded){unload_game();loaded=false;}if(handle){deinit();dlclose(handle);handle=nullptr;}reset_core=nullptr;cheat_reset=nullptr;cheat_set=nullptr;rom.clear();frame.clear();samples.clear();width=height=pixfmt=0;hasPicture=false;sampleRate=32040.0;fps=60.0988;}
extern "C" JNIEXPORT jstring JNICALL Java_br_com_knightjoga_Core_open(JNIEnv *env,jclass,jstring path,jbyteArray bytes,jstring rom_file,jstring save,jboolean multitap){
 close_core();const char* p=env->GetStringUTFChars(path,nullptr);handle=dlopen(p,RTLD_NOW|RTLD_LOCAL);env->ReleaseStringUTFChars(path,p);
 if(!handle){const char* e=dlerror();return env->NewStringUTF(e?e:"Núcleo ausente");}
 if(!(bind(set_env,"retro_set_environment")&&bind(set_video,"retro_set_video_refresh")&&bind(set_audio,"retro_set_audio_sample")&&bind(set_audio_batch,"retro_set_audio_sample_batch")&&bind(set_poll,"retro_set_input_poll")&&bind(set_state,"retro_set_input_state")&&bind(init,"retro_init")&&bind(deinit,"retro_deinit")&&bind(load_game,"retro_load_game")&&bind(unload_game,"retro_unload_game")&&bind(run,"retro_run")&&bind(reset_core,"retro_reset")&&bind(controller,"retro_set_controller_port_device")&&bind(get_av,"retro_get_system_av_info")&&bind(get_info,"retro_get_system_info")&&bind(get_memory_data,"retro_get_memory_data")&&bind(get_memory_size,"retro_get_memory_size")&&bind(serialize_size,"retro_serialize_size")&&bind(serialize,"retro_serialize")&&bind(unserialize,"retro_unserialize"))){dlclose(handle);handle=nullptr;return env->NewStringUTF("Núcleo incompatível com libretro");}
 bind(cheat_reset,"retro_cheat_reset");bind(cheat_set,"retro_cheat_set");
 const char* d=env->GetStringUTFChars(save,nullptr);saveDir=d;env->ReleaseStringUTFChars(save,d);
 set_env(::env);set_video(video);set_audio(audio);set_audio_batch(batch);set_poll(poll);set_state(state);init();
 jsize n=env->GetArrayLength(bytes);if(n<128||n>16*1024*1024){close_core();return env->NewStringUTF("ROM inválida ou muito grande");}
 rom.resize(n);env->GetByteArrayRegion(bytes,0,n,(jbyte*)rom.data());
 const char* rp=env->GetStringUTFChars(rom_file,nullptr);romPath=rp;env->ReleaseStringUTFChars(rom_file,rp);
 system_info si{};get_info(&si);game_info game{romPath.c_str(),si.need_fullpath?nullptr:rom.data(),si.need_fullpath?0:rom.size(),nullptr};
 if(!load_game(&game)){close_core();return env->NewStringUTF("O núcleo não conseguiu abrir esta ROM. Confira o formato e o arquivo.");}
 loaded=true;multitapDevice=multitap==JNI_TRUE;controller(0,1);controller(1,multitapDevice?257:1);av_info av{};get_av(&av);sampleRate=av.time.sample_rate;fps=av.time.fps;return nullptr;
}
extern "C" JNIEXPORT void JNICALL Java_br_com_knightjoga_Core_setMultitap(JNIEnv*,jclass,jboolean enabled){
 if(loaded&&controller){multitapDevice=enabled==JNI_TRUE;controller(1,multitapDevice?257:1);}
}
extern "C" JNIEXPORT jboolean JNICALL Java_br_com_knightjoga_Core_reset(JNIEnv*,jclass){
 if(!loaded||!reset_core)return JNI_FALSE;
 for(auto& input:inputs)input=0;
 samples.clear();reset_core();
 return JNI_TRUE;
}
extern "C" JNIEXPORT void JNICALL Java_br_com_knightjoga_Core_step(JNIEnv *env,jclass,jintArray masks){if(!loaded)return;jint m[5]={};env->GetIntArrayRegion(masks,0,5,m);for(int i=0;i<5;i++)inputs[i]=(uint16_t)m[i];samples.clear();run();}
extern "C" JNIEXPORT jintArray JNICALL Java_br_com_knightjoga_Core_pixels(JNIEnv* env,jclass){jintArray a=env->NewIntArray(frame.size());if(a&&!frame.empty())env->SetIntArrayRegion(a,0,frame.size(),(const jint*)frame.data());return a;}
extern "C" JNIEXPORT jshortArray JNICALL Java_br_com_knightjoga_Core_audio(JNIEnv* env,jclass){jshortArray a=env->NewShortArray(samples.size());if(a&&!samples.empty())env->SetShortArrayRegion(a,0,samples.size(),samples.data());return a;}
extern "C" JNIEXPORT jint JNICALL Java_br_com_knightjoga_Core_width(JNIEnv*,jclass){return width;}
extern "C" JNIEXPORT jint JNICALL Java_br_com_knightjoga_Core_height(JNIEnv*,jclass){return height;}
extern "C" JNIEXPORT jboolean JNICALL Java_br_com_knightjoga_Core_hasPicture(JNIEnv*,jclass){return hasPicture?JNI_TRUE:JNI_FALSE;}
extern "C" JNIEXPORT jint JNICALL Java_br_com_knightjoga_Core_rate(JNIEnv*,jclass){return (int)sampleRate;}
extern "C" JNIEXPORT jdouble JNICALL Java_br_com_knightjoga_Core_fps(JNIEnv*,jclass){return fps;}
extern "C" JNIEXPORT void JNICALL Java_br_com_knightjoga_Core_close(JNIEnv*,jclass){close_core();}

extern "C" JNIEXPORT jbyteArray JNICALL Java_br_com_knightjoga_Core_snapshot(JNIEnv* env,jclass){if(!loaded)return nullptr;size_t n=serialize_size();if(!n||n>16*1024*1024)return nullptr;std::vector<uint8_t> bytes(n);if(!serialize(bytes.data(),n))return nullptr;jbyteArray out=env->NewByteArray(n);if(out)env->SetByteArrayRegion(out,0,n,(const jbyte*)bytes.data());return out;}
extern "C" JNIEXPORT jboolean JNICALL Java_br_com_knightjoga_Core_restore(JNIEnv* env,jclass,jbyteArray input){if(!loaded||!input)return false;jsize n=env->GetArrayLength(input);std::vector<uint8_t> bytes(n);env->GetByteArrayRegion(input,0,n,(jbyte*)bytes.data());bool restored=unserialize(bytes.data(),n);if(restored&&controller)controller(1,multitapDevice?257:1);return restored;}

extern "C" JNIEXPORT jbyteArray JNICALL Java_br_com_knightjoga_Core_saveRam(JNIEnv* env,jclass){if(!loaded)return nullptr;size_t n=get_memory_size(0);void* ptr=get_memory_data(0);if(!ptr||n==0||n>2*1024*1024)return nullptr;jbyteArray a=env->NewByteArray(n);if(a)env->SetByteArrayRegion(a,0,n,(const jbyte*)ptr);return a;}
extern "C" JNIEXPORT void JNICALL Java_br_com_knightjoga_Core_loadRam(JNIEnv* env,jclass,jbyteArray a){if(!loaded||!a)return;size_t n=get_memory_size(0);void* ptr=get_memory_data(0);if(ptr&&n==size_t(env->GetArrayLength(a)))env->GetByteArrayRegion(a,0,n,(jbyte*)ptr);}

static bool valid_cheat(const std::string& input){
 if(input.empty()||input.size()>200)return false;
 size_t start=0,count=0;
 while(start<input.size()){
  size_t end=input.find('+',start);if(end==std::string::npos)end=input.size();
  const size_t length=end-start;
  if(++count>24||(length!=8&&length!=9))return false;
  const bool genie=length==9&&input[start+4]=='-';
  const bool replay=length==9&&input[start+6]==':';
  if(length==9&&genie==replay)return false;
  for(size_t i=start;i<end;i++){
   char character=input[i];
   if((genie&&i==start+4)||(replay&&i==start+6))continue;
   if(!std::isxdigit(static_cast<unsigned char>(character)))return false;
  }
  start=end+1;if(end+1==input.size())return false;
 }
 return true;
}
extern "C" JNIEXPORT jboolean JNICALL Java_br_com_knightjoga_Core_setCheats(JNIEnv* env,jclass,jobjectArray codes){
 if(!loaded||!cheat_reset||!cheat_set||!codes)return JNI_FALSE;
 jsize n=env->GetArrayLength(codes);if(n>64)return JNI_FALSE;
 std::vector<std::string> verified;verified.reserve(n);
 for(jsize i=0;i<n;i++){
  jstring item=(jstring)env->GetObjectArrayElement(codes,i);
  if(!item)return JNI_FALSE;
  jsize length=env->GetStringUTFLength(item);
  if(length<=0||length>200){env->DeleteLocalRef(item);return JNI_FALSE;}
  const char* utf=env->GetStringUTFChars(item,nullptr);
  if(!utf){env->DeleteLocalRef(item);return JNI_FALSE;}
  std::string code(utf,length);env->ReleaseStringUTFChars(item,utf);env->DeleteLocalRef(item);
  if(!valid_cheat(code))return JNI_FALSE;
  verified.push_back(std::move(code));
 }
 // Rebuild active groups on every toggle: the core never removes a disabled group itself.
 cheat_reset();
 for(unsigned i=0;i<verified.size();i++)cheat_set(i,true,verified[i].c_str());
 return JNI_TRUE;
}
