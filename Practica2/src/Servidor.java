import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
/*
type:
* Tipo Instrucción = 0
* Tipo File = 1
* Tipo String = 2
* tipo Acuse = 3

Instrucciones:
* 0 = ls remoto ruta Álbumes
* 1 = ls remoto ruta Album + nombre
* 2 = recibe carrito de compras?

Formato de Paquetes de llegada
* int type
* byte[] data
*/

public class Servidor {
    private static int tam = 1000;//un kilobyte
    private static int port = 1234;
    private static int clientPort = 0;
    private static String dir = "127.0.0.1";// Se debe de reemplazar con la del cliente
    private static final String ruta = "src/Albumes";
    private static int TamVentana = 5;

    public static void main(String[] args){
        try{
            DatagramSocket ds = new DatagramSocket(port);

            while(true){

                recibeInstr(ds);// Servidor esperando instrucción de cliente
            }
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e){
            e.printStackTrace();
        }

    }//End Main

    public static void recibeInstr(DatagramSocket ds) throws IOException {
        int type,instr;
        final File Base = new File(ruta);
        System.out.println("Servidor esperando Instrucciones..");
        DatagramPacket p = new DatagramPacket(new byte[65535],65535);
        ds.setSoTimeout(0);// Se espera un tiempo indefinido a que llegue la operación a realizar
        ds.receive(p); // recibe paquete entrante en búsqueda de un paquete que sea instrucción
        ds.setSoTimeout(10000);//Se ponen para que espere máximo 10 mil milisegundos

        InetAddress IA = p.getAddress();//Se enviarán las respuestas a esta dirección entrante
        clientPort = p.getPort();

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(p.getData())); //comienza la lectura del paquete
        type = dis.readInt();// Primero se lee un entero para describir el tipo de paquete que llegó
        System.out.println("Se recibió instrucción tipo: ");
        if (type==0){
            instr=dis.readInt();// lee un siguiente número para saber que operación fue solicitada
            switch (instr){
                case 0:
                    System.out.println("LS album");
                    sendDirContent(Base,ds,IA); // Caso 0 se manda el listado de álbumes, es decir se manda un ls de la carpeta
                    break;
                case 1:
                    System.out.println("LS canciones");
                    String album = dis.readUTF();// Caso 1 se lee un String UTF buscando del nombre de un álbum
                    File Album = getAlbum(album);
                    sendDirContent(Album,ds,IA);// Se manda un ls listando las canciones disponibles en ese álbum
                    break;
                case 2:
                    System.out.println("Manda Canción");
                    getSong(ds, IA, p);// Caso 2: Se manda a recibir el nombre y album de la canción que se desea descargar
                    //Esta instrucción se manda múltiples veces cuando se desea descargar un carrito completo
                    break;
                default:
                    System.out.println("Operación desconocida: ERROR");//Se recibe una operación desconocida == error!
                    break;
            }
        }else{
            System.out.println("Error de lectura de Paquete");
            System.out.println("Esperando siguiente paquete...");
        }

    }
    public static boolean recibeACK(DatagramSocket s) throws IOException{//Esta función recibe ACK es decir
        // recibe confirmación de llegada y regresa un booleano
        boolean respuesta = false;//Se inicializa la respuesta
        System.out.println("Servidor esperando Acuse de Recibido...");
        DatagramPacket p = new DatagramPacket(new byte[65535],65535);
        s.receive(p);//Socket bloqueante que espera 2000 milisegundos respuesta del cliente

        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(p.getData()));
        int type = dis.readInt();//Define el tipo en este caso se espera el tipo acuse: 3
        /*if(type != 3)
        sout
            throw new IOException("Se esperaba ACK...");*/
        System.out.println("Type: "+type);
        int res = dis.readInt(); // Se espera uno o cero: Respuesta de recibido

        if(res>0)//Si se lee positivo es verdadero
            respuesta = true ;
        return respuesta;
    }

    public static void sendDirContent(File dir, DatagramSocket ds, InetAddress IA) throws IOException {
        int Type = 2; // Se inicializa un envío de tipo 2
        String[] albumList = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if(name.equals(".DS_Store"))
                    return false;
                return true;
            }
        }); //Se recibe un arreglo con la lista de nombres en el directorio
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        //Formato de recibido de envío de ls
        dos.writeInt(Type);//Se escribe el tipo de contenido: tipo 2
        dos.writeInt(albumList.length); // Se manda el número de nombres que se envían
        for(String x : albumList){
            dos.writeUTF(x);//Se escribe cada nombre en el datagrama
            System.out.println(x);
        }
        dos.flush();

        byte[] send = baos.toByteArray();//Se escribe en bytes la información que se va a enviar
        boolean ack = false;//mientras no se reciba el acuse de recibido se mantiene enviándose la información
        while (!ack){
            DatagramPacket p = new DatagramPacket(send, send.length, IA, clientPort);
            ds.send(p);//Enviado del paquete
            System.out.println("paquete enviado");
            ack = recibeACK(ds);//recibe el ACK por parte del cliente
        }// manda hasta que reciba el ack en true
    }//End of sendDirContent (String List)

    public static void getSong(DatagramSocket ds, InetAddress IA, DatagramPacket p)throws IOException{
        System.out.println("Servidor extrayendo nombre Album, extrayendo nombre canción");
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(p.getData()));

        int type = dis.readInt();// lee el tipo de datagrama que se recibió
        int instr = dis.readInt();
        String Album = dis.readUTF(); // Se lee el nombre del album
        String name = dis.readUTF(); // Se lee el nombre de la canción

        File tmp = new File(ruta + File.separator + Album + File.separator + name);
        sendSong(tmp,ds,IA);
    }
    public static File getAlbum(String albumName){
        File temp = new File(ruta + File.separator + albumName);
        return temp;
    }//Local
    public static int mandaVentana(List<byte[]> listaPaquetes, DatagramSocket cl, InetAddress IA, int NumVentana) throws IOException {
        int enviados = 0;
        int i  = TamVentana * NumVentana;
        while((listaPaquetes.get(i) != null) && (i < ((TamVentana*NumVentana)+TamVentana))){
            DatagramPacket p = new DatagramPacket(listaPaquetes.get(i), listaPaquetes.get(i).length, IA, clientPort);//Enviado del paquete
            cl.send(p);
            i++;
            enviados++;
        }
        boolean res = recibeACK(cl); // recibe booleano de confirmación de recibido
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
        }//while: realiza una lista de bytes listos para escribirse en datagrama

        while(recibidos<numPacket){
            int temp = recibidos;
            recibidos = recibidos + mandaVentana(listSend,cl,IA,numVentana);
            if(temp < recibidos)// checa si es mayor el número de recibidos
                numVentana++;
        }//end While (Envío de ventana)
    }//end sendFile Method
}//End Clase Servidor
