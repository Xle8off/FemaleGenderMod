import com.wildfire.gui.ChatChannelMetadata;
public final class PublicChatCheck {
    public static void main(String[] args){
        var toastReveal=new com.wildfire.gui.ToastControlsReveal();
        if(com.wildfire.gui.ToastControlsReveal.height(true,0)!=43||
           com.wildfire.gui.ToastControlsReveal.height(true,1)!=68||
           com.wildfire.gui.ToastControlsReveal.height(false,1)!=43)
            throw new AssertionError("No action-row space before chat opens");
        if(toastReveal.update(false,0)!=0)throw new AssertionError("Closed notification compact");
        if(toastReveal.update(true,1_000_000L)!=0)throw new AssertionError("Expansion starts continuously");
        double partial=toastReveal.update(true,111_000_000L);
        if(partial<=0||partial>=1)throw new AssertionError("Smooth notification expansion");
        if(toastReveal.update(false,111_000_000L)!=partial)throw new AssertionError("Continuous reversal");
        if(toastReveal.update(false,331_000_000L)!=0)throw new AssertionError("Compact after chat closes");
        Object nativeInput=new Object(),multiline=new Object();
        Object[] focused={nativeInput};int[] forced={0},blurred={0};
        if(!com.wildfire.gui.ChatFocusRecovery.ensure(multiline,()->focused[0],
            target->{if(target==nativeInput)focused[0]=target;},
            target->{focused[0]=target;forced[0]++;},old->{if(old==nativeInput)blurred[0]++;}))
            throw new AssertionError("Recover restrictive EditBox-only override");
        if(focused[0]!=multiline||forced[0]!=1||blurred[0]!=1)throw new AssertionError("Transfer focus ownership");
        if(com.wildfire.gui.ChatFocusRecovery.ensure(multiline,()->focused[0],
            target->{throw new AssertionError("Already focused");},target->{forced[0]++;},old->{}))
            throw new AssertionError("Do not repeat focus recovery");
        if(com.wildfire.gui.ChatFocusRecovery.ensure(nativeInput,()->focused[0],
            target->focused[0]=target,target->{throw new AssertionError("Normal focus accepted");},old->{}))
            throw new AssertionError("Preserve native command focus");
        String control="springplus:chat-state:global:true:false";
        var payload=net.minecraft.network.chat.Component.literal("\u200B").withStyle(
            net.minecraft.network.chat.Style.EMPTY.withInsertion(control));
        if(!control.equals(ChatChannelMetadata.controlInsertion(payload)))throw new AssertionError("Root control");
        if(!control.equals(ChatChannelMetadata.controlInsertion(net.minecraft.network.chat.Component.empty().append(payload))))
            throw new AssertionError("Wrapped control");
        if(ChatChannelMetadata.controlInsertion(net.minecraft.network.chat.Component.literal("Hello"))!=null)
            throw new AssertionError("Normal message is not control");
        var playerReveal=new com.wildfire.gui.MiniPlayerReveal();
        var track=new com.wildfire.gui.MiniPlayerReveal.Track("Track","0:30",0xFFFFFFFF,.5f);
        if(playerReveal.update(track,true,0).visibility()!=0)throw new AssertionError("Player enters from below");
        if(playerReveal.update(track,true,220_000_000L).visibility()!=1)throw new AssertionError("Player fully enters");
        var closing=playerReveal.update(null,true,220_000_000L);
        if(!closing.track().equals(track)||closing.visibility()!=1)throw new AssertionError("Track retained for exit");
        var middle=playerReveal.update(null,true,330_000_000L);
        if(middle.visibility()<=0||middle.visibility()>=1)throw new AssertionError("Player smoothly exits");
        if(playerReveal.update(track,true,330_000_000L).visibility()!=middle.visibility())throw new AssertionError("Player reversal continuous");
        playerReveal.update(null,true,550_000_000L);
        if(playerReveal.update(null,true,770_000_000L)!=null)throw new AssertionError("Stopped card released");
        playerReveal.reset();
        if(playerReveal.update(null,true,800_000_000L)!=null)throw new AssertionError("No old server track");
        if(com.wildfire.gui.ChatHeaderReveal.offset(0,24)!=24)throw new AssertionError("Header begins below");
        if(com.wildfire.gui.ChatHeaderReveal.offset(240_000_000L,24)!=0)throw new AssertionError("Header ends at rest");
        float previous=24;
        for(long time=0;time<=260_000_000L;time+=1_000_000L){
            float offset=com.wildfire.gui.ChatHeaderReveal.offset(time,24);
            if(offset<0||offset>previous)throw new AssertionError("Monotonic header animation");
            previous=offset;
        }
        try{
            var directory=java.nio.file.Files.createTempDirectory("chat-view-check-");
            var file=directory.resolve("filters.txt");
            java.nio.file.Files.writeString(file,"@vanillaChat\n@hideTabs\nstaff\n");
            var preferences=new com.wildfire.gui.ChatAllFilters(file);
            if(!preferences.vanillaChat()||preferences.showTabs()||preferences.visible("staff"))throw new AssertionError("Saved chat view");
            preferences.toggleVanillaChat();
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            while(new com.wildfire.gui.ChatAllFilters(file).vanillaChat()&&System.nanoTime()<deadline)Thread.sleep(10);
            if(new com.wildfire.gui.ChatAllFilters(file).vanillaChat())throw new AssertionError("Chat view toggle persisted");
            java.nio.file.Files.deleteIfExists(file);java.nio.file.Files.deleteIfExists(directory);
        }catch(Exception error){throw new AssertionError("Chat view preferences",error);}
        if(com.wildfire.gui.ChatScrollBounds.maximum(new int[]{14,14,14},60)!=0)throw new AssertionError("No overflow");
        if(com.wildfire.gui.ChatScrollBounds.maximum(new int[]{14,14,14},42)!=0)throw new AssertionError("Exact fit");
        if(com.wildfire.gui.ChatScrollBounds.maximum(new int[]{14,14,14},30)!=12)throw new AssertionError("Partial overflow");
        if(com.wildfire.gui.ChatScrollBounds.maximum(new int[]{74,14},60)!=28)throw new AssertionError("Sticker overflow");
        for(String channel:new String[]{"global","local","staff","discord","system","spy"})if(!ChatChannelMetadata.channel("springplus:chat:"+channel).equals(channel))throw new AssertionError(channel);
        for(String invalid:new String[]{"[G] global","springplus:chat:admin","springplus:chat:staff:evil",""})if(!ChatChannelMetadata.channel(invalid).equals("system"))throw new AssertionError(invalid);
        var capability=ChatChannelMetadata.capability("springplus:chat-state:local:false");if(capability==null||capability.staff()||!capability.sending().equals("local"))throw new AssertionError();
        if(!ChatChannelMetadata.capability("springplus:chat-state:staff:true").staff())throw new AssertionError();
        if(!ChatChannelMetadata.capability("springplus:chat-state:global:true:true").spy())throw new AssertionError();
        if(ChatChannelMetadata.capability("springplus:chat-state:global:false:false").spy())throw new AssertionError();
        if(ChatChannelMetadata.capability("springplus:chat-state:global:true:yes")!=null)throw new AssertionError();
        if(ChatChannelMetadata.capability("springplus:chat-state:global:true:true:extra")!=null)throw new AssertionError();
        for(String invalid:new String[]{"springplus:chat-state:global:yes","springplus:chat-state:discord:true","springplus:chat-state:staff:true:extra","springplus:chat-state:staff:",""})if(ChatChannelMetadata.capability(invalid)!=null)throw new AssertionError(invalid);
        if(ChatChannelMetadata.capability(null)!=null||!ChatChannelMetadata.channel(null).equals("system"))throw new AssertionError();
        var source=net.minecraft.network.chat.Component.literal("[G] ").withStyle(net.minecraft.ChatFormatting.GREEN)
            .append(net.minecraft.network.chat.Component.literal("Nick: ").withStyle(net.minecraft.ChatFormatting.GOLD))
            .append(net.minecraft.network.chat.Component.literal("[Стикер:test/one]"));
        var prefix=com.wildfire.gui.PublicChatState.prefix(source,10);
        if(!prefix.getString().equals("[G] Nick: "))throw new AssertionError("Media prefix length");
        var colors=new java.util.ArrayList<Integer>();
        prefix.visit((style,value)->{if(!value.isEmpty())colors.add(style.getColor().getValue());return java.util.Optional.empty();},net.minecraft.network.chat.Style.EMPTY);
        if(!colors.equals(java.util.List.of(0x55FF55,0xFFAA00)))throw new AssertionError("Media prefix colors: "+colors);
        System.out.println("Public chat metadata checks passed: channels, staff capability, malformed state, legacy fallback");
    }
}
