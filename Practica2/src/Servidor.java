import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
/*
type:
* Tipo Instrucción = 0
* Tipo File = 1
* Tipo String = 2
* tipo Acuse = 3
* */

/*
Instrucciones:
* 0 = ls remoto ruta Álbumes
* 1 = ls remoto ruta Album + nombre
* 2 = recibe carrito de compras?
* */

/*
Formato de Paquetes de llegada
* int type
* byte[] data
 */

public class Servidor {
    private static int tam = 1000;//un kilobyte
    private static int port = 1234;
    private static String dir = "127.0.0.1";// Se debe de reemplazar con la del cliente
    private static final String ruta = "/Users/erickcode/Documents/Practica2/src/Albumes";
    private static int TamVentana = 5;

    public static void setTam(int newTam) {
        tam = newTam;
    }
    public static void setPort(int newPort) {
        port = newPort;
    }
    public static void setDir(String newDir) {
        dir = newDir;
    }

    public static void main(String[] args){

    }//End Main

    public static void oneFuncToRuleThemAll(DatagramSocket ds, InetAddress IA) throws IOException {
        ds.setSoTimeout(1000);//Se ponen para que espere máximo mil milisegundos
        int type,instr;
        final File Base = new File(ruta);

        System.out.println("Servidor esperando Instrucciones..");
        DatagramPacket p = new DatagramPacket(new byte[65535],65535);
        ds.receive(p);

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(p.getData()));
        type = dis.readInt();
        if (type==0){
            instr=dis.readInt();
            switch (instr){
                case 0:
                    sendDirContent(Base,ds,IA);
                    break;
                case 1:
                    String album = dis.readUTF();
                    File Album = getAlbum(album);
                    sendDirContent(Album,ds,IA);
                    break;
                case 2:
                    getSong(ds);
                    break;
                default:
                    System.out.println("Operación desconocida: ERROR");
                    break;
            }
        }else
            System.out.println("Error de lectura de Paquete");
    }
    public static boolean recibeACK(DatagramSocket s) throws IOException{
        boolean respuesta = false;
        System.out.println("Servidor esperando Acuse de Recibido..");
        DatagramPacket p = new DatagramPacket(new byte[65535],65535);
        s.receive(p);

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(p.getData()));
        int type = dis.readInt();//Define el tipo en este caso se espera el tipo acuse
        int res = dis.readInt(); // Se espera uno o cero

        if(res>0)
            respuesta = true ;
        return respuesta;
    }
    public static File getSong(DatagramSocket ds)throws IOException{
        System.out.println("Servidor esperando nombre de la canción a mandar");
        DatagramPacket p = new DatagramPacket(new byte[65535],65335);
        ds.receive(p);
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(p.getData()));

        int type = dis.readInt();
        String Album = dis.readUTF();
        String name = dis.readUTF();

        File tmp = new File(ruta + File.separator + Album + File.separator + name);
        return tmp;
    }
    public static void getCarrito(DatagramSocket ds,DatagramPacket p,InetAddress IA) throws IOException {
        System.out.println("Servidor esperando carrito de compras de cliente: " + p.getAddress().toString());
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(p.getData()));

        int type = dis.readInt();
        int numCanciones = dis.readInt();// Se recibe el número de canciones o paquetes a esperar
        for(int i=0;i<numCanciones;i++){
            File temp = getSong(ds);
            sendSong(temp,ds,IA);
        }

    }//End getCarrito
    public static File getAlbum(String albumName){
        File temp = new File(ruta + File.separator + albumName);
        return temp;
    }//Local
    public static void sendDirContent(File dir, DatagramSocket ds, InetAddress IA) throws IOException {
        int Type = 2;
        String[] albumList = dir.list();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        //Formato de recibido de envío de ls
        dos.writeInt(Type);
        dos.writeInt(albumList.length);
        for(String x : albumList)
            dos.writeUTF(x);
        dos.flush();

        byte[] send = baos.toByteArray();
        boolean ack = false;
        while (!ack){
            DatagramPacket p = new DatagramPacket(send, send.length, IA, port);//Enviado del paquete
            ds.send(p);
            ack = recibeACK(ds);
        }// manda hasta que reciba el ack en true
    }//End of sendDirContent (String List)
    public static int mandaVentana(List<byte[]> listaPaquetes, DatagramSocket cl, InetAddress IA, int NumVentana) throws IOException {
        int enviados = 0;
        int i  = TamVentana*NumVentana;
        while(listaPaquetes.get(i) != null && i < ((TamVentana*NumVentana)+TamVentana) ){
            DatagramPacket p = new DatagramPacket(listaPaquetes.get(i), listaPaquetes.get(i).length, IA, port);//Enviado del paquete
            cl.send(p);
            i++;
            enviados++;
        }
        boolean res = recibeACK(cl);
        if(res)
            return enviados;
        else
            return 0;
    }//end Method mandaVentana
    public static void sendSong(File song, DatagramSocket cl, InetAddress IA) throws IOException {
        List<byte[]> listSend = new ArrayList<byte[]>();
        int Type = 1;
        long songLength = song.length();
        String nombre = song.getName(), ruta = song.getPath();
        System.out.println("Preparándose pare enviar canción: " + nombre + "\n\n");
        DataInputStream dis = new DataInputStream(new FileInputStream(ruta));
        int readB, numPacket = 0,recibidos = 0,numVentana = 0, tp = ((int) songLength) / tam;
        long enviados = 0;
        if(song.length()%tam>0)
            tp++;
        while(numPacket<tp){//Se utiliza la longitud del archivo
            numPacket++;
            //Envío del paquete:
            byte[] b = new byte[tam]; //Lectura del archivo a leer
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            //Preparación del Paquete FORMATO ESTÁNDAR
            dos.writeInt(Type);//Primero se envía el tipo
            dos.writeUTF(song.getName()); // Se envía el nombre de la canción en el paquete
            dos.writeInt(numPacket);//Después se envía el número del paquete
            dos.writeInt(tp);//Se envía número de paquetes que se planea que reciba.
            readB = dis.read(b);//Se leen 1kB de información
            dos.write(b);//Se adjunta 1kB a los metadatos
            dos.flush();//Se escriben en el DOS
            byte[] send = baos.toByteArray();//Se hace el Byte[] contenedor del PAQUETE CON METADATOS

            listSend.add(send);
            enviados = enviados + readB;
        }//while
        while(recibidos<numPacket){
            int temp = recibidos;
            recibidos = recibidos + mandaVentana(listSend,cl,IA,numVentana);
            if(temp < recibidos)// checa si es mayor el número de recibidos
                numVentana++;
        }//end While (Envío de ventana)
    }//end sendFile Method
}//End Clase Servidor
