#!/usr/bin/env ruby

require "nokogiri"
require "fileutils"

ALL_VIEWS = ["TextView",
  "ListView",
  "ImageView",
  "ImageButton",
  "item",
  "ToggleButton",
  "CheckBox",
  "RadioButton",
  "CheckedTextView",
  "Spinner",
  "ProgressBar",
  "SeekBar",
  "QuickContactBadge",
  "RadioGroup",
  "RatingBar",
  "EditText",
  "TableRow",
  "ExpandableListView",
  "GridView",
  "ScrollView",
  "HorizontalScrollView",
  "SearchView",
  "SlidingDrawer",
  "LinearLayout",
  "FrameLayout",
  "TabHost",
  "TabWidget",
  "WebView",
  "Gallery",
  "MediaController",
  "VideoView",
  "TimePicker",
  "DatePicker",
  "CalendarView",
  "Chronometer",
  "AnalogClock",
  "DigitalClock",
  "ImageSwitcher",
  "AdapterViewFlipper",
  "StackView",
  "TextSwitcher",
  "ViewAnimator",
  "ViewFlipper",
  "ViewSwitcher",
  "View",
  "ViewStub",
  "menu/item",
  "Button"]

def add_id(d)
  Dir.glob(File.join(d, "**", "*.xml")) do |f|
    puts f
    doc = Nokogiri::XML(File.read(f))
    before = doc.to_xml
    ALL_VIEWS.each do |v_name|
      doc.xpath("//" + v_name).each do |v|
        id = v["android:id"]
        if id.nil?
          v["android:id"] = "@+id/" + v.name + "_" + (Random.rand * 100000000).to_i.to_s
          puts v["android:id"]
        end
      end
    end
    after = doc.to_xml
    #if before.eql? after
    #  puts "******************"
    #else
      #puts "##################"
      #FileUtils.mv(f, f + ".original")
      FileUtils.rm(f)
      File.open(f, "w") do |n_f|
        n_f << doc.to_xml
      end
    #end
  end
end

ARGV.each do |d|
  Dir.glob(File.join(d, "**", "res", "layout*")) do |f|
    add_id(f)
  end

  Dir.glob(File.join(d, "**", "res", "menu*")) do |f|
    add_id(f)
  end
end

__END__

Please make sure you have Nokogiri installed before running this script.
You can get Nokogiri through a simple command: gem install Nokogiri

