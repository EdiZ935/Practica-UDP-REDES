import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Cliente {
    private static int tam = 10;
    private static int port = 1234;
    private static String dir = "127.0.0.1";

    private final static String ruta = "/Users/erickcode/Documents/Practica2/src/Descargas";

    private static List<String> namSongs = new ArrayList<String>();
    private static List<String> namAlbum = new ArrayList<String>();

    public static void setTam(int newTam){
        tam = newTam;
    }
    public static void setPort(int newPort){
        port = newPort;
    }
    public static void setDir(String newDir){
        dir = newDir;
    }

    public static void main(String[] args) {
        try {
            DatagramSocket ds = new DatagramSocket();
            InetAddress IA = InetAddress.getByName(dir);
            final String[] albumNameList = recibeCatalogo(ds,0,null, IA);
        }catch (Exception e){
            e.printStackTrace();
        }
    }//End of main

    public static void sendACK(DatagramSocket ds, InetAddress IA, boolean resultado) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(3);
        if(resultado)
            dos.writeInt(1);
        else
            dos.writeInt(0);
        dos.flush();

        byte[] send = baos.toByteArray();
        DatagramPacket p = new DatagramPacket(send, send.length,IA,port);
        ds.send(p);
    }
    public static void getSong(String Album, String song, DatagramSocket ds, InetAddress IA) throws IOException{
        File descargas = new File(ruta);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(2); //Tipo archivo
        dos.writeUTF(Album);
        dos.writeUTF(song);
        dos.flush();
        byte[] petition = baos.toByteArray();
        DatagramPacket pet = new DatagramPacket(petition, petition.length,IA,port);
        ds.send(pet);

        //Empieza lectura de canción
        if(!buscaAlbum(Album,descargas)){
            File f = new File(ruta + File.separator + Album);
            if(f.mkdir())
                System.out.println("File created");
            else
                System.out.println("File can´t be created");
        }
        DataOutputStream salidaArchivo = new DataOutputStream(new FileOutputStream(ruta+File.separator+Album+File.separator+song));
        boolean leyendo = true;
        long recibidos = 0;
        int window = 0;
        List<byte[]> file = new ArrayList<>();
        while (leyendo){
            for(int i = 0;i < 5;i++){
                try{
                    DatagramPacket ventana = new DatagramPacket(new byte[65535],65535);
                    ds.receive(ventana);
                    DataInputStream dis = new DataInputStream(new ByteArrayInputStream(ventana.getData()));
                    int type = dis.readInt();
                    String Name = dis.readUTF();
                    int NoPaq = dis.readInt();
                    int NumTotal = dis.readInt();
                    byte[] paquete = new byte[1000];
                    int read = dis.read(paquete);

                    if(type != 1)
                        sendACK(ds,IA,false);
                    else{
                        file.add(NoPaq-1,paquete);//Insertion sort
                        if(file.size() == NumTotal){//Se comprueba si se acabó de leer los paquetes
                            leyendo=false;
                            i=5;
                            break;
                        }
                    }
                }catch (SocketTimeoutException s){
                    s.printStackTrace();
                    sendACK(ds,IA,false);
                }
            }//END FOR Ventana
            sendACK(ds,IA,true);//Siguiente ventana
        }//END WHILE LEYENDO
        for (byte[] b : file) {
            salidaArchivo.write(b,0,b.length);
            salidaArchivo.flush();
        }
        System.out.println("Se ha escrito el archivo...");
    }
    public static boolean buscaAlbum(String Album,File descargas ){
        boolean res = false;
        String[] arreglo = descargas.list();

        for(String x: arreglo){
            if(x.equals(Album))
                res = true;
        }
        return res;
    }
    public static String[] recibeCatalogo(DatagramSocket ds, int album_O_catalog, String album, InetAddress IA) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(0);//Type = 0 Instrucción
        if(album_O_catalog>0){
            dos.writeInt(1);
            dos.writeUTF(album);
        }else
            dos.writeInt(0);
        dos.flush();
        byte[] send = baos.toByteArray();
        DatagramPacket p = new DatagramPacket(send, send.length,IA, port);
        ds.send(p);

        DatagramPacket cat = new DatagramPacket(new byte[65535],65535);
        ds.receive(cat);
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(cat.getData()));

        if(dis.readInt()==2)
            sendACK(ds,IA,true);// Se envía ACK
        else
            sendACK(ds,IA,false);

        int tamLista = dis.readInt(), i = 0;
        String[] Lista = new String[tamLista];
        while(i<tamLista){
            Lista[i] = dis.readUTF();
        }
        return Lista;
    }

    public static void AddToCart(String Album, String Song) {
        namSongs.add(Song);
        namAlbum.add(Album);
    } // Será local y se envía Lista

    public static void getCart(DatagramSocket ds, InetAddress IA) throws IOException{
        int i = 0;
        for(String song : namSongs){
            getSong(namAlbum.get(i),song,ds,IA);
            i++;
        }

    }
}//End of class Cliente.java