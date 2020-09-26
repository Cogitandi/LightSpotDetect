
package Prog;


        import java.util.ArrayList;
        import java.util.List;
         
        import org.opencv.core.Core;
        import org.opencv.core.Mat;
        import org.opencv.core.MatOfPoint;
        import org.opencv.core.MatOfPoint2f;
        import org.opencv.core.Point;
        import org.opencv.core.Rect;
        import org.opencv.core.Scalar;
        import org.opencv.highgui.HighGui;
        import org.opencv.imgproc.Imgproc;
        import org.opencv.imgproc.Moments;
        import org.opencv.videoio.VideoCapture;
        import org.opencv.videoio.Videoio;
         
        public class App {
          /*
            arg[0] - Sciezka do filmu {filmy/film.avi}
            arg[1] - Minimalna ilosc zapalonych punktow na obrazie do uznania za zapalenie
            arg[2] - Liczba klatek, ktore program pomija, jezeli miedzy nimi nastapila zmiana liczby punktow swiecacych, powodujacych informacje o zapaleniu/zgaszeniu swiatla
            arg[3] - Czy ma sie pojawic okno z przetwarzanym filmem {true/false}
            */
          public static void main(String[] args) throws Exception {
            if(args.length<3) {
              System.out.println("Musisz podac pierwsze trzy parametry do rozruchu programu");
              System.out.println("Przykład: 'filmy/pierwszy.avi 3 2 true'");
              return;
            }
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);   // Zaladowanie biblioteki OpenCV
            VideoCapture cap = new VideoCapture();          // Deklaracja klasy do przechwytywania klatek
            List < MatOfPoint > contours;                   // Deklaracja listy zawierajacej wykryte kontury w klatce
            Mat src = new Mat();                            // Pojemnik na pojedyncza bez naniesionych zmian klatke z filmy
            Mat result = new Mat();                         // Pojemnik na pojedyczna klatke z filmu po przetworzeniu
            Integer counter = 0;                            // Licznik klatek
         
            String path = args[0];                          // Wczytanie sciezki do filmu
            int requiredLights = Integer.valueOf(args[1]);  // Wczytanie minimalnej ilosci punktow
            int skippedFrames = Integer.valueOf(args[2]);   // Wczytanie liczby pomijanych klatek
			boolean showWindows;
			if(args.length>3)
				showWindows = Boolean.valueOf(args[3]);
			else
				showWindows=false;
         
            cap.open(path); // Zaladowanie filmu
            if (!cap.isOpened()) {
              // Zamkniecie programu jezeli proba otwarcia filmu przebiegla niepomyslnie
              System.out.println("Nie udalo sie otworzyc pliku");
              return;
            }
            informationMessage(path, requiredLights, skippedFrames); // Informacja o ustawieniach przetwarzanego filmu
            ArrayList < Integer > listaKlatek = new ArrayList < >(); // Lista numerow klatek w ktorych nastapila zmiana stanu (światło się zaświeciło lub zgasło) oraz numery pominietych(np. światło zgasło na 2 klatki)
          
            boolean isShining = false; // Flaga oznaczająca czy przekroczony jest próg liczby świateł (true - świeci się wystarczająca liczba świateł)
            while (cap.read(src) != false) {
              // Przetwarzanie filmu
              result = filter(src);                         //filtrowanie klatki, aby znaleźć świecące punkty
              contours = retriveContours(result);           //wykrycie konturów, oznaczających punkty świecące
              int lightNumber = matchSquare(contours, src); //zaznaczenie świateł, zwrócenie ich liczby dla danej klatki
              isShining = ifShining(listaKlatek, isShining, lightNumber, requiredLights, counter);
              if(showWindows) {
                displayFrameWithMarks("result", src, counter, lightNumber); // Wyświetlenie klatki z zaznaczonymi światłami, numerem klatki i liczbą znalezionych świateł
              }
         
              counter++;
            }
         
            // Zwolnienie klatek
            cap.release();
            //src.release();
            //result.release();
           
            if(showWindows) {
              HighGui.destroyAllWindows();
          }
         
            // Sprawdzenie listy numerów klatek, aby wyeleminować te między którymi jest zbyt mała różnica np. 1 klatki między zaświeceniem i zgaszeniem
            for (int i = 1; i < listaKlatek.size(); i++) {
              if (listaKlatek.get(i) - listaKlatek.get(i - 1) < skippedFrames) {
                // Jeśli liczba klatek między zmianami stanu świateł jest mniejsza od podanej przez użytkownika wartośći to nie jest uwzgledniana
                listaKlatek.set(i - 1, -1); // Wpisanie w miejsce numerów klatek wartości -1, nie będą one uwzględniane w  wyniku
                listaKlatek.set(i, -1);     // Wpisanie w miejsce numerów klatek wartości -1, nie będą one uwzględniane w  wyniku
                i++;
              }
            }
            // Wyświetlenie podsumowania w jakich klatkach zaświecone było światło, z pominięciem powyżej wykluczonych klatek
            for (int i = 0; i < listaKlatek.size(); i++) {
              if (listaKlatek.get(i) != -1) {
                if (i % 2 == 0)
                {
                    System.out.println("Światło zaświeca się w klatce nr" + listaKlatek.get(i));
                }
                else  {
                    System.out.println("Światło gaśnie się w klatce nr" + listaKlatek.get(i));
                }
              }
            }
         
           
            System.out.println("Koniec przetwarzania");
            System.exit(0);
            return; // Zamkniecie programu
         
          }
         
         
         
          private static boolean ifShining(ArrayList < Integer > listaKlatek, boolean isShining, int lightNumber, int requiredLights, int counter) {
            if (lightNumber >= requiredLights) //sprawdzenie czy liczba źródeł światła przekracza próg podany przez użytkownika
            {
              if (!isShining) { //jeśli światło było zgaszone, to zaznaczane jest jego zaświecenie
                listaKlatek.add(counter); //dodanie numeru klatki do listy
                return true;
              }
            }
            else {
              if (isShining) { //jeśli światło było zaświecone, to zaznaczane jest jego zgaszenie
                listaKlatek.add(counter); //dodanie numeru klatki do listy
                return false;
              }
            }
            return isShining;
          }
         
          // Funkcja filtrująca klatkę, aby zostały na niej tylko punkty świetlne, zaznaczone białym kolorem na czarnym tle
          private static Mat filter(Mat frame) {
            Mat result = new Mat();
            Imgproc.bilateralFilter(frame, result, 15, 180, 180); // Nalozenie filtru dwustronnego
            result.convertTo(result, -1, 1, -5);                  // Konwersja kolorow
            Core.inRange(result, new Scalar(150, 100, 210), new Scalar(200, 255, 235), result); //150,80,210
            Imgproc.dilate(result, result, new Mat(), new Point(1, 1), 3); // Rozszerzenie wykrytych punktow
            Imgproc.erode(result, result, new Mat(), new Point(1, 1), 1);  // Pomniejszenie rozmiar punktow
            return result;
          }
          // Funkcja zwarajaca wykryte kontury w klatce
          private static List < MatOfPoint > retriveContours(Mat frame) {
            List < MatOfPoint > contours = new ArrayList < >();
            Mat hierarchy = new Mat();
            Imgproc.findContours(frame, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE); // Zapis znalezionych konturow do listy
            return contours;
         
          }
          // Funkcja zwracajaca liczbe wykrytych punktow swiecacych, ktore sa kwadratami
          private static int matchSquare(List < MatOfPoint > contours, Mat frameToDrawCircle) {
            int lightNumber = 0;
            int i = 0;
            double[][] tab = new double[contours.size()][2];
         
            for (MatOfPoint item: contours) {
              boolean ifExists = false;
              Moments moments = Imgproc.moments(item);
              Point centerPoint = new Point(); // Punkt centralny konturu
              centerPoint.x = moments.get_m10() / moments.get_m00();
              centerPoint.y = moments.get_m01() / moments.get_m00();
              tab[i][0] = centerPoint.x;
              tab[i][1] = centerPoint.y;
         
              if (isSquare(item)) {
                // Jezeli kontur jest kwadratem
                Imgproc.circle(frameToDrawCircle, centerPoint, 20, new Scalar(237, 41, 50));
                // Zaznaczenie swiecacego punktu na klatce okregiem
                for (int k = 0; k < i; k++) {
                  if (Math.abs(tab[k][0] - tab[i][0]) < 2.5 & Math.abs(tab[k][1] - tab[i][1]) < 2.5) ifExists = true;
                }
                if (!ifExists) lightNumber++;
              }
              i++;
         
            }
            return lightNumber;
          }
         
          // Funkcja sprawdzajaca czy wykryty kontur jest kwadratem
          private static boolean isSquare(MatOfPoint matOfPoint) {
            MatOfPoint2f shape = new MatOfPoint2f(matOfPoint.toArray());
            MatOfPoint2f out = new MatOfPoint2f();
            double len = Imgproc.arcLength(shape, true);
            Imgproc.approxPolyDP(shape, out, 0.04 * len, true); // Wyfiltrowanie pojedynczych punktow
            Rect rect = Imgproc.boundingRect(out); // Stworzenie figury prostokata z konturu
            double ratio = rect.width > rect.height ? (double) rect.width / rect.height: (double) rect.height / rect.width; // SPrawdzenie stosunku wysokosci do szerokosci punktu
            return ratio < 2 ? true: false;
          }
          // Wyswietlanie informacji o parametrach dzialania programu
          private static void informationMessage(String path, int minLightPoints, int skipFramesAmount) {
            System.out.println(
             "\nPrzetwarzany film: " + path +
             "\nSwiatlo uznawane jest za wykryte, gdy jest co najmniej: " + minLightPoints + " zrodel swiatla,"+
             "\nProgram pomija momenty w ktorych swiatla gasna lub zapalaja sie na mniej niz " + skipFramesAmount + " klatki");
          }
         
          private static void displayFrameWithMarks(String name, Mat frame, Integer counter, int lightNumber) {
            Imgproc.putText(frame, counter.toString(), new Point(200, 200), Imgproc.FONT_HERSHEY_COMPLEX, 5.0, new Scalar(100)); // Nalozenie numeru klatki
            // if(lightNumber>3)
            Imgproc.putText(frame, String.valueOf(lightNumber), new Point(700, 700), Imgproc.FONT_HERSHEY_COMPLEX, 5.0, new Scalar(100)); // Wyswietlenie ilosci swiecacych punktow
            HighGui.namedWindow(name, HighGui.WINDOW_AUTOSIZE); // Nazwa okna
            HighGui.imshow(name, frame); // Wyswietlenie klatki
            HighGui.waitKey(1); // Czas wyswietlenia
          }
        }

