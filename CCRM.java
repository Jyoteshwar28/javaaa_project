import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

// ======================== ENUMS ========================
enum Semester { SPRING, SUMMER, FALL }
enum Grade {
    S(10), A(9), B(8), C(7), D(6), E(5), F(0);
    private final int points;
    Grade(int p){ this.points = p; }
    public int getPoints(){ return points; }
    public static Grade fromMarks(double m){
        if(m>=90) return S;
        if(m>=80) return A;
        if(m>=70) return B;
        if(m>=60) return C;
        if(m>=50) return D;
        if(m>=40) return E;
        return F;
    }
}

// ===================== CUSTOM EXCEPTIONS =====================
class DuplicateEnrollmentException extends Exception {
    public DuplicateEnrollmentException(String msg){ super(msg);}
}
class MaxCreditLimitExceededException extends RuntimeException {
    public MaxCreditLimitExceededException(String msg){ super(msg);}
}

// ===================== SINGLETON CONFIG =====================
class AppConfig {
    private static AppConfig instance;
    private final Path dataFolder;
    private AppConfig(){ 
        dataFolder = Paths.get(System.getProperty("user.home"),"ccrm-data"); 
        try { Files.createDirectories(dataFolder); } catch(IOException e){ e.printStackTrace();}
    }
    public static AppConfig getInstance(){
        if(instance==null) instance=new AppConfig();
        return instance;
    }
    public Path getDataFolder(){ return dataFolder; }
}

// ===================== ABSTRACT CLASS =====================
abstract class Person {
    protected String id, fullName, email;
    protected LocalDateTime createdAt;
    public Person(String id,String name,String email){
        assert id!=null && name!=null && email!=null;
        this.id=id; this.fullName=name; this.email=email;
        createdAt=LocalDateTime.now();
    }
    public abstract String profile();
}

// ===================== STUDENT =====================
class Student extends Person {
    String regNo;
    boolean active=true;
    Map<String,Enrollment> enrollments = new HashMap<>();
    public Student(String id,String name,String email,String regNo){
        super(id,name,email); this.regNo=regNo;
    }
    public void enroll(Course c) throws DuplicateEnrollmentException{
        if(enrollments.containsKey(c.code)) throw new DuplicateEnrollmentException("Already enrolled in "+c.code);
        enrollments.put(c.code,new Enrollment(this,c));
    }
    public double getGPA(){
        return enrollments.values().stream()
                .mapToDouble(e -> e.grade!=null?e.grade.getPoints():0)
                .average().orElse(0.0);
    }
    @Override
    public String profile(){
        return fullName+" ("+regNo+") Active:"+active+" GPA:"+String.format("%.2f",getGPA());
    }
}

// ===================== INSTRUCTOR =====================
class Instructor extends Person {
    String empId, department;
    public Instructor(String id,String name,String email,String empId,String dept){
        super(id,name,email); this.empId=empId; this.department=dept;
    }
    @Override
    public String profile(){ return fullName+" ["+department+"]"; }
}

// ===================== COURSE WITH BUILDER =====================
class Course {
    final String code,title,instructorId,department;
    final int credits;
    final Semester semester;
    private Course(Builder b){
        code=b.code; title=b.title; credits=b.credits; instructorId=b.instructorId;
        semester=b.semester; department=b.department;
    }
    static class Builder {
        private String code,title,instructorId,department;
        private int credits; private Semester semester;
        public Builder code(String c){ code=c; return this; }
        public Builder title(String t){ title=t; return this; }
        public Builder credits(int c){ credits=c; return this; }
        public Builder instructor(String i){ instructorId=i; return this; }
        public Builder semester(Semester s){ semester=s; return this; }
        public Builder department(String d){ department=d; return this; }
        public Course build(){
            if(code==null||title==null) throw new IllegalArgumentException("Code/Title required");
            return new Course(this);
        }
    }
    @Override public String toString(){ return code+"-"+title+"("+credits+"cr) ["+semester+"]"; }
}

// ===================== ENROLLMENT =====================
class Enrollment {
    Student student; Course course; LocalDateTime enrolledAt; Grade grade;
    public Enrollment(Student s, Course c){ student=s; course=c; enrolledAt=LocalDateTime.now(); }
}

// ===================== FILE UTILITIES =====================
class FileUtil {
    public static void backup(Path src, Path dest) throws IOException{
        Files.walk(src).forEach(p->{
            try{
                Path d=dest.resolve(src.relativize(p));
                if(Files.isDirectory(p)) Files.createDirectories(d);
                else Files.copy(p,d,StandardCopyOption.REPLACE_EXISTING);
            }catch(IOException e){ throw new RuntimeException(e);}
        });
    }
    public static long dirSize(Path path) throws IOException{
        long total=0;
        for(Path p: Files.list(path).collect(Collectors.toList())){
            if(Files.isDirectory(p)) total+=dirSize(p);
            else total+=Files.size(p);
        }
        return total;
    }
    public static List<Student> importStudents(Path csv) throws IOException{
        List<Student> list=new ArrayList<>();
        List<String> lines = Files.readAllLines(csv);
        for(String line: lines.subList(1, lines.size())){
            String[] cols=line.split("\\|");
            list.add(new Student(cols[0],cols[2],cols[3],cols[1]));
        }
        return list;
    }
    public static List<Course> importCourses(Path csv) throws IOException{
        List<Course> list=new ArrayList<>();
        List<String> lines = Files.readAllLines(csv);
        for(String line: lines.subList(1, lines.size())){
            String[] cols=line.split("\\|");
            Course c=new Course.Builder().code(cols[0]).title(cols[1])
                    .credits(Integer.parseInt(cols[2]))
                    .semester(Semester.valueOf(cols[3]))
                    .department(cols[4]).build();
            list.add(c);
        }
        return list;
    }
    public static void exportStudents(List<Student> students, Path out) throws IOException{
        try(BufferedWriter bw=Files.newBufferedWriter(out)){
            bw.write("id|regNo|name|email\n");
            for(Student s: students)
                bw.write(s.id+"|"+s.regNo+"|"+s.fullName+"|"+s.email+"\n");
        }
    }
    public static void exportCourses(List<Course> courses, Path out) throws IOException{
        try(BufferedWriter bw=Files.newBufferedWriter(out)){
            bw.write("code|title|credits|semester|department\n");
            for(Course c: courses)
                bw.write(c.code+"|"+c.title+"|"+c.credits+"|"+c.semester+"|"+c.department+"\n");
        }
    }
}

// ===================== MAIN CLI =====================
public class CCRM {
    static Scanner sc = new Scanner(System.in);
    static Map<String,Student> students = new HashMap<>();
    static Map<String,Course> courses = new HashMap<>();

    public static void main(String[] args) throws IOException{
        System.out.println("=== CCRM START === Data folder: "+AppConfig.getInstance().getDataFolder());
        boolean run=true;
        while(run){
            System.out.println("\n1.Add Student 2.Add Course 3.Enroll & Grade 4.Print Transcript");
            System.out.println("5.Import CSV 6.Export CSV 7.Backup 8.Reports 0.Exit");
            System.out.print("Choose: "); String ch=sc.nextLine();
            switch(ch){
                case "1"-> addStudent();
                case "2"-> addCourse();
                case "3"-> enrollAndGrade();
                case "4"-> printTranscripts();
                case "5"-> importCSV();
                case "6"-> exportCSV();
                case "7"-> backupDemo();
                case "8"-> reportGPA();
                case "0"-> { run=false; System.out.println("Exiting..."); }
                default -> System.out.println("Invalid choice");
            }
        }
    }

    // -------------------- CLI METHODS --------------------
    static void addStudent(){
        System.out.print("ID: "); String id=sc.nextLine();
        System.out.print("RegNo: "); String reg=sc.nextLine();
        System.out.print("Name: "); String name=sc.nextLine();
        System.out.print("Email: "); String email=sc.nextLine();
        students.put(reg,new Student(id,name,email,reg));
        System.out.println("Added "+name);
    }

    static void addCourse(){
        System.out.print("Code: "); String code=sc.nextLine();
        System.out.print("Title: "); String title=sc.nextLine();
        System.out.print("Credits: "); int cr=Integer.parseInt(sc.nextLine());
        Course c=new Course.Builder().code(code).title(title).credits(cr)
                .semester(Semester.FALL).department("CSE").build();
        courses.put(code,c);
        System.out.println("Added "+c);
    }

    static void enrollAndGrade(){
        System.out.print("Student RegNo: "); String r=sc.nextLine();
        System.out.print("Course Code: "); String c=sc.nextLine();
        Student s=students.get(r); Course course=courses.get(c);
        if(s==null||course==null){ System.out.println("Invalid student/course"); return; }
        try{
            s.enroll(course);
            System.out.print("Enter marks (0-100): ");
            double m=Double.parseDouble(sc.nextLine());
            s.enrollments.get(course.code).grade=Grade.fromMarks(m);
            System.out.println("Enrolled "+s.fullName+" to "+course.code+" Grade:"+s.enrollments.get(course.code).grade);
        }catch(DuplicateEnrollmentException e){ System.out.println(e.getMessage()); }
    }

    static void printTranscripts(){
        students.values().forEach(s->{
            System.out.println("\n--- "+s.fullName+" Transcript ---");
            s.enrollments.values().forEach(e->
                System.out.println(e.course.code+" | "+e.course.title+" | Grade: "+(e.grade!=null?e.grade:"N/A"))
            );
            System.out.println("GPA: "+String.format("%.2f",s.getGPA()));
        });
    }

    static void importCSV() throws IOException{
        System.out.print("Students CSV path: "); Path sPath=Paths.get(sc.nextLine());
        System.out.print("Courses CSV path: "); Path cPath=Paths.get(sc.nextLine());
        for(Student s: FileUtil.importStudents(sPath)) students.put(s.regNo,s);
        for(Course c: FileUtil.importCourses(cPath)) courses.put(c.code,c);
        System.out.println("Import completed.");
    }

    static void exportCSV() throws IOException{
        FileUtil.exportStudents(new ArrayList<>(students.values()),Paths.get("students_export.csv"));
        FileUtil.exportCourses(new ArrayList<>(courses.values()),Paths.get("courses_export.csv"));
        System.out.println("Export completed to students_export.csv & courses_export.csv");
    }

    static void backupDemo(){
        try{
            Path src=Paths.get(".");
            String stamp=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path dest=AppConfig.getInstance().getDataFolder().resolve("backup_"+stamp);
            Files.createDirectories(dest);
            FileUtil.backup(src,dest);
            long size=FileUtil.dirSize(dest);
            System.out.println("Backup done at "+dest+" Total size:"+size+" bytes");
        }catch(IOException e){ e.printStackTrace();}
    }

    static void reportGPA(){
        System.out.println("\n--- GPA Distribution ---");
        Map<Integer,Long> dist=students.values().stream()
                .collect(Collectors.groupingBy(s->(int)Math.floor(s.getGPA()),Collectors.counting()));
        dist.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e-> System.out.println("GPA "+e.getKey()+" : "+e.getValue()+" students"));
        System.out.println("\n--- Top Students ---");
        students.values().stream().sorted((a,b)->Double.compare(b.getGPA(),a.getGPA()))
                .limit(5)
                .forEach(s->System.out.println(s.fullName+" GPA:"+String.format("%.2f",s.getGPA())));
    }
}
