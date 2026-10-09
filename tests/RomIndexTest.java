package br.com.knightjoga;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;

/** Exercises the persistent index policy for unchanged, replaced and deleted ROMs. */
public final class RomIndexTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] ignored) throws Exception {
        File index=Files.createTempFile("luni-index-test-", ".bin").toFile();
        long old=System.currentTimeMillis()-10000;
        String hash="a".repeat(64),source="content://sample/roms/test.sfc";
        ArrayList<RomIndex.Item> items=new ArrayList<>();
        items.add(new RomIndex.Item("Teste",null,"sfc","root/teste","root/teste",hash));
        RomIndex initial=new RomIndex("folder:sample");
        initial.sources.put(source,new RomIndex.Source("test.sfc",8192,old,items));
        initial.save(index);

        RomIndex restored=RomIndex.load(index,"folder:sample");
        check(restored.sources.size()==1,"índice persistido");
        RomIndex.Source cached=restored.sources.get(source);
        check(cached.reusable("test.sfc",8192,old),"inalterado reutiliza hash");
        check(!cached.reusable("test.sfc",8193,old),"novo tamanho invalida cache");
        check(!cached.reusable("test.sfc",8192,old+1000),"modificação invalida cache");
        check(!cached.reusable("outro.sfc",8192,old),"nome alterado invalida cache");
        check(!cached.reusable("test.sfc",8192,0),"provedor sem data exige leitura");
        check(RomIndex.load(index,"folder:other").sources.isEmpty(),"trocar pasta isola índice");

        RomIndex afterDeletion=new RomIndex("folder:sample");
        afterDeletion.save(index);
        check(RomIndex.load(index,"folder:sample").sources.isEmpty(),"arquivo excluído some do índice");
        Files.write(index.toPath(),new byte[]{1,2,3});
        check(RomIndex.load(index,"folder:sample").sources.isEmpty(),"índice corrompido é reconstruído");
        index.delete();
        System.out.println("RomIndexTest OK");
    }
}
