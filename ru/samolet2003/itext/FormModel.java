package ru.samolet2003.itext;

import java.util.Date;

public class FormModel {
    private String fio;
    private boolean sexMale;
    private boolean sexFemale;
    private Date birthDate;

    public String getFio() {
        return fio;
    }

    public void setFio(String fio) {
        this.fio = fio;
    }

    public boolean isSexMale() {
        return sexMale;
    }

    public void setSexMale(boolean sexMale) {
        this.sexMale = sexMale;
    }

    public boolean isSexFemale() {
        return sexFemale;
    }

    public void setSexFemale(boolean sexFemale) {
        this.sexFemale = sexFemale;
    }

    public Date getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(Date birthDate) {
        this.birthDate = birthDate;
    }
}
