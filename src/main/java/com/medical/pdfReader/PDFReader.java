package com.medical.pdfReader;

import com.medical.config.AppConfig;
import com.medical.domain.MedicalPoint;
import com.medical.domain.MedicalUnitType;
import com.medical.domain.Specialty;
import com.medical.service.MedicalPointService;
import com.medical.service.MedicalUnitTypeService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

public class PDFReader {

    static int skipFirstLines = 8;
    static ParsedMedicalPoint medicalPoint = new ParsedMedicalPoint();
    static Vector<ParsedMedicalPoint> medicalPoints = new Vector<ParsedMedicalPoint>();
    static String line = "";
    static String profileRegex = ".*[^.1-9]2\\.[1-9]*\\.";

    static void printMedicalPoints() {
        for(int i=0; i<medicalPoints.size(); i++)
        {
            medicalPoints.get(i).printData();
        }
    }

    static void saveMedicalPointsToDatabase() throws Exception {
        AbstractApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        MedicalPointService pointService = (MedicalPointService) context.getBean("medicalPointService");
        MedicalUnitTypeService unitTypeService = ( MedicalUnitTypeService) context.getBean("medicalUnitTypeService");

        //for(int i=0; i<medicalPoints.size(); i++)
        for(int i=6; i<7; i++)
        {
            String fullName = medicalPoints.get(i).getFullName();
            MedicalPoint point = pointService.addMedicalPointWithName(fullName);

            Set<Specialty> specialties = new HashSet<Specialty>();

            for(int j=0; j<medicalPoints.get(i).profile.size(); j++)
            {
                // TU DOPISAC JAK BEDZIE TRZEBA WIECEJ SPECJALIZACJI
                if(medicalPoints.get(i).profile.get(j).contains("DERMATOLOGIA")) // id 7
                {
                    Specialty specialty = new Specialty();
                    specialty.setId(7);
                    specialties.add(specialty);
                }
                if(medicalPoints.get(i).profile.get(j).contains("ORTOPEDIA")) // id 25
                {
                    Specialty specialty = new Specialty();
                    specialty.setId(25);
                    specialties.add(specialty);
                }
                if(medicalPoints.get(i).profile.get(j).contains("OKULISTYKA")) // id 23
                {
                    Specialty specialty = new Specialty();
                    specialty.setId(23);
                    specialties.add(specialty);
                }
                if(medicalPoints.get(i).profile.get(j).contains("OTORYNOLARYNGOLOGIA")) // id 26
                {
                    Specialty specialty = new Specialty();
                    specialty.setId(26);
                    specialties.add(specialty);
                }
            }

            if (medicalPoints.get(i).sor == true) {
                MedicalUnitType unitType = unitTypeService.findByName("Szpitalny Oddział Ratunkowy");
                pointService.addMedicalUnit("SOR", unitType, point, specialties);
            }
            if (medicalPoints.get(i).nocnaSwiatecznaPomoc == true) {
                MedicalUnitType unitType = unitTypeService.findByName("Nocna i świąteczna opieka zdrowotna");
                pointService.addMedicalUnit("Nocna i świąteczna opieka zdrowotna", unitType, point, specialties);
            }
            if (medicalPoints.get(i).izbaPrzyjec == true) {
                MedicalUnitType unitType = unitTypeService.findByName("Izba przyjęć");
                pointService.addMedicalUnit("Izba przyjęć", unitType, point, specialties);
            }
            MedicalUnitType unitType = unitTypeService.findByName("Oddział Szpitalny");
            pointService.addMedicalUnit("Oddział Szpitalny", unitType, point, specialties);
        }
    }

    static void saveMedicalPoint() {
        if(medicalPoint.nazwaZakladu != null) {
            medicalPoints.add(medicalPoint);
            medicalPoint = new ParsedMedicalPoint();
        }
    }

    static void parseLine() {
        line = line.toUpperCase();
        if(skipFirstLines > 0)
            --skipFirstLines;
        else if(line.contains("1.3. ") || line.contains("1.3 ")) {
            saveMedicalPoint();

            String nazwaZakladu = line.replace("1.3. NAZWA ZAKŁADU LECZNICZEGO: ", "");
            medicalPoint.nazwaZakladu = nazwaZakladu;
        }
        else if(line.contains("1.4. ") || line.contains("1.4 ")) {
            String adresZakladu = line.replace("1.4. ADRES ZAKŁADU LECZNICZEGO: ", "");
            medicalPoint.adresZakladu = adresZakladu;
        }
        else if(line.contains("PORADA SPECJALISTYCZNA")) {
            int index = line.indexOf("3");
            if(index == -1)
                index = 0;

            String specjalnosc = line.substring(0, Math.min(line.length(), index));
            specjalnosc = specjalnosc.replace("PORADA SPECJALISTYCZNA – ", "");
            specjalnosc = specjalnosc.replace("PORADA SPECJALISTYCZNA - ", ""); // Dolnośląskie ma chyba inny myślnik albo biały znak

            if(!medicalPoint.profile.contains(specjalnosc))
                medicalPoint.profile.add(specjalnosc);
        }
        else if(line.matches(profileRegex)) {
            if(line.contains("IZBA"))
                medicalPoint.izbaPrzyjec = true;
            else if(line.contains("SOR") || line.contains("SZPITALNY ODDZIAŁ RATUNKOWY"))
                medicalPoint.sor = true;
            else {
                int index = line.indexOf("2");
                String profil = line.substring(0, Math.min(line.length(), index));

                medicalPoint.profile.add(profil);
            }
        }
        else if(line.contains("ŚWIADCZENIA NOCNEJ I ŚWIĄTECZNEJ")) {
            medicalPoint.nocnaSwiatecznaPomoc = true;
        }
    }

    static void parseText(String text) {
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            while ((line = reader.readLine()) != null) {
                parseLine();
                //System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception {
        /*
        Kompatybilność z PDFami:
        7/16

        Działają:
            Mazowieckie
            Dolnośląskie
            Kujawsko-Pomorskie
            Podkarpackie
            Swiętokrzyskie
            Warminsko-Mazurskie
            Wielkopolskie

        Wymagają poprawek:
            Lodzkie
            Lubelskie
            Lubuskie
            Malopolskie
            Opolskie
            Podlaskie
            Pomorskie
            Sląskie
            Zachodniopomorskie

            TO DO:
                - x dla dzieci == x dziecięca, usunięcie powtórzeń jeśli gdzieś są
        */


        PDDocument document = PDDocument.load(new File("pdf/mazowieckie.pdf"));
        if (!document.isEncrypted()) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            parseText(text);
            printMedicalPoints();
        }
        document.close();
        saveMedicalPointsToDatabase();
    }
}
