#!/use/bin/env ruby

doc = Array.new
ARGV.each_with_index do |f, idx|
  doc[idx] = Nokogiri::XML(File.read(f))
end

s1 = doc[0].to_xml
s2 = doc[1].to_xml

f1 = "/tmp/f1.xml"
f2 = "/tmp/f2.xml"
File.open(f1, "w") { |f| f << s1 } 
File.open(f2, "w") { |f| f << s2 } 

system "diff #{f1} #{f2}"
