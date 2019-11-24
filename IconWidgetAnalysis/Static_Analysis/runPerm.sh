

for app in `ls /Users/shaoyang/Desktop/DeepIntent_Example/benign/*.apk`
do
echo $app
java -jar /Users/shaoyang/Downloads/Static_Analysis/APKCallGraph.jar $app /Users/shaoyang/Desktop/DeepIntent_Example/benign/ /Users/shaoyang/Downloads/Static_Analysis/img2widgets/ /Users/shaoyang/Downloads/Static_Analysis/permission_output/ /Users/shaoyang/Downloads/Static_Analysis/ic3/output/
done
