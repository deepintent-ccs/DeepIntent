#!/usr/bin/env ruby

while line = gets
  #puts line
  puts "+ \"" + line.rstrip.gsub(/"/, '\\\\"') + "\" + \"\\n\""
end

__END__
