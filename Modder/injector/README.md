# Injector 
Tools for injecting ace engine's library
to an android apk/game
## 

compile with android studio

Build -> Build Bundles/APK(s) -> Build APK(s)
https://stackoverflow.com/a/54002115/14073678

## generate smali

```sh
./gen_smali.sh
```
## Injection

- use apk tool to get `AndroidManifest`

- parse the `AndroidManifest.xml` to find
  the main activity if not found throw an error

- redo the decompilation with `-r` option 
  to specify to not decompile resource file
  because sometimes decompiling resource might
  throw error "brute erro"

### Manual Steps

0. decompile the apk using 

   (use `-r` so we can recompile it again without error) 
   ```sh
   apktool d ./[my_apk] -r
   ```
1. generate neccessary code and library by 
   running `python3 ./gen_smali.py`, it should create 
   folder called `code_to_inject`

2. put each `liblib_ACE.so` in `code_to_inject/lib` to each arch folder like `x86`, `x86_64`
   of the decompiled apk

3. copy `code_to_inject/smali/com/example/` to decompiled apk's `smali/com` folder

4. find launchable activity so we can inject the injector's init function 
   to the apk via AndroidManifest.xml (can be generated by using apktool without -r option)

   this is done by finding activity tag  in the manifest which has intent filter tag
   and inside that in has 
   `<action android:name="android.intent.action.MAIN"/>` and 
   `<category android:name="android.intent.category.LAUNCHER"/>`

   this seems to indicate that the activity is the main one,

   to get the path of main smali file, it can be seen at one of
   attributes of `activity` tag  called android name

   which has something like
   ```
	android:name="com.shatteredpixel.shatteredpixeldungeon.android.AndroidLauncher"
   ```
   that are the path of smali and  can easily get the path
   to that smali by replacing `.` with `/` for path

5. find `onCreate` function (seems to be like the main function
   for android apk), 

   and inject the injector's init code (can be seen with 
   `./gen_smali.py` script and put 

   ```
	invoke-static {}, Lcom/AceInjector/utils/Injector;->Init()V
   ```
   at the start of the `onCreate` function



6. check the `adb logcat` if it indeed is injected 
   it should say something like
   ```
        Log.d("AceTheGame", "Code Is injected :D");
   ```


# License
[AGPL3](./LICENSE)
![](./docs/asset/License.png)

