import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.List;

public class Cliente {
    private static int tam = 1000;
    private static int port = 1234;
    private static String dir = "127.0.0.1";
    private final static String ruta = "src/Descargas";
    private static List<String> namSongs = new ArrayList<String>();
    private static List<String> namAlbum = new ArrayList<String>();

    public static void main(String[] args) {
        try {
            DatagramSocket ds = new DatagramSocket(123);
            ds.setSoTimeout(10000);
            InetAddress IA = InetAddress.getByName(dir);
            System.out.println("--- Album root: ---");
            String[] albumNameList = recibeCatalogo(ds, IA,false,null);
            boolean salir = false;
            while(!salir){
                System.out.println(
                        "Seleccione una instrucción:\n" +
                        "0 - listar catálogo álbumes.\n" +
                        "1 - listar catálogo canciones\n" +
                        "2 - añadir al carrito una canción\n" +
                        "3 - finalizar compra carrito\n" +
                        "4 - descargar una canción\n" +
                        "5 - salir...\n");
                Scanner scan = new Scanner(System.in);
                System.out.print("Introduzca el número: ");
                int num = scan.nextInt();
                if(num!=5)
                    mandaInstr(num,ds,IA);
                else
                    salir=true;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }//End of main

    public static void mandaInstr(int instr, DatagramSocket ds, InetAddress IA) throws IOException {
        String album,song;
        Scanner scan = new Scanner(System.in);
        switch (instr){
            case 0:
                System.out.println("--- Album root: ---");
                recibeCatalogo(ds,IA,false,null);
                break;
            case 1:
                System.out.println("Escribe el nombre del album que deseas navegar:");
                album = scan.nextLine();
                System.out.println("--- Album "+album+": ---");
                recibeCatalogo(ds, IA,true, album);
                break;
            case 2:
                System.out.println("Escribe el album de la canción que se va a agregar al carro: ");
                album = scan.nextLine();
                System.out.println("--- Album "+album+": ---");
                recibeCatalogo(ds, IA,true, album);
                System.out.println("Escribe la canción que se va a agregar: ");
                song = scan.nextLine();
                AddToCart(album,song);
                System.out.println("Canción "+song+" descargada...");
                break;
            case 3:
                getCart(ds,IA);
                System.out.println("Se ha descargado el carrito solicitado...");
                break;
            case 4:
                System.out.println("Selecciona la canción a descargar... ");
                System.out.println("Escribe el nombre del album: ");
                album = scan.nextLine();
                System.out.println("--- Album "+album+": ---");
                recibeCatalogo(ds, IA,true, album);
                System.out.println("Escribe la canción a descargar: ");
                song = scan.nextLine();
                getFile(album,song,ds,IA);
                break;
            default:
                System.out.println("Función desconocida o no implementada... ");
                break;
        }
    }
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
    public static void getFile(String Album, String song, DatagramSocket ds, InetAddress IA) throws IOException{
        File descargas = new File(ruta);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(0); //Tipo instrucción
        dos.writeInt(2); // Instrucción solicitada
        dos.writeUTF(Album); // Se manda el nombre del album
        dos.writeUTF(song);
        dos.flush();
        byte[] petition = baos.toByteArray();
        DatagramPacket pet = new DatagramPacket(petition, petition.length,IA,port);
        ds.send(pet);

        //Empieza lectura de canción
        if(!buscaDir(Album, descargas)){ //Se busca el nombre del album para crearlo si no existe
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

                    if(type != 1) {
                        sendACK(ds, IA, false);
                        break;
                    }else{
                        file.add(NoPaq-1,paquete);//Insertion sort
                        if(file.size() == NumTotal){//Se comprueba si se acabó de leer los paquetes
                            leyendo=false;
                            sendACK(ds,IA,true);// Se manda un ultimo ACK confirmando la llegada del paquete
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
    public static boolean buscaDir(String Album,File descargas){
        boolean res = false;
        String[] arreglo = descargas.list();
        if(arreglo == null)
            return false;
        for(String x: arreglo){ // Si hay una coincidencia con algún elemento se devuelve true
            if(x.equals(Album))
                res = true;
        }
        return res;
    }
    public static String[] recibeCatalogo(DatagramSocket ds, InetAddress IA, boolean Album, String album) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(0);//Type = 0 Instrucción
        if(Album){
            dos.writeInt(1);//Tipo de instrucción
            dos.writeUTF(album); //
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
        System.out.println("--- Contenido : ---");
        String[] Lista = new String[tamLista];
        while(i<tamLista){
            Lista[i] = dis.readUTF();
            System.out.println(Lista[i]);
            i++;
        }
        System.out.println("--- End Contenido ---");
        return Lista;
    }

    public static void AddToCart(String Album, String Song) {
        namSongs.add(Song); // Se añade al carrito la canción
        namAlbum.add(Album);// En el mismo
    } // Será local y se envía Lista

    public static void getCart(DatagramSocket ds, InetAddress IA) throws IOException{
        int i = 0;
        for(String song : namSongs){
            getFile(namAlbum.get(i),song,ds,IA);
            i++;
        }

    }
}//End of class Cliente.java