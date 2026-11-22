package kr.example.haliltalk;

import android.content.Intent;
import android.view.View;
import java.util.HashSet;
import java.util.Set;

/** Question menus and actual annotated screenshot walkthroughs on the current viewport. */
public final class VisualGuideFlowTest extends GuestActivityTestCase {
    private MainActivity activity;

    @Override protected void setUp() throws Exception {
        super.setUp();
        activity=(MainActivity)getInstrumentation().startActivitySync(
                new Intent(getInstrumentation().getTargetContext(),MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
        awaitWindowFocus(activity.getWindow());
    }

    @Override protected void tearDown() throws Exception {
        try{
            getInstrumentation().runOnMainSync(()->activity.finish());
            getInstrumentation().waitForIdleSync();
        }finally{super.tearDown();}
    }

    private HelpGuide open(String context) throws Throwable {
        runTestOnUiThread(()->HelpGuide.show(activity,context));
        getInstrumentation().waitForIdleSync();
        HelpGuide guide=(HelpGuide)activity.getFragmentManager().findFragmentByTag(HelpGuide.TAG);
        assertNotNull(guide);awaitWindowFocus(guide.getDialog().getWindow());
        return guide;
    }

    private void click(HelpGuide guide,int id) throws Throwable {
        runTestOnUiThread(()->assertTrue(guide.getDialog().findViewById(id).performClick()));
        getInstrumentation().waitForIdleSync();
    }

    private void shot(HelpGuide guide,String name) throws Exception {
        EnhancementCapture.capture(getInstrumentation(),guide.getDialog().getWindow(),name,"visual-guide-qa");
    }

    private void scrollToEnd(HelpGuide guide) throws Throwable {
        EnhancementCapture.awaitFrame(getInstrumentation(),guide.getDialog().getWindow());
        runTestOnUiThread(()->{
            android.widget.ScrollView scroll=guide.getDialog().findViewById(R.id.help_scroll);
            scroll.setSmoothScrollingEnabled(false);
            scroll.fullScroll(View.FOCUS_DOWN);
            assertFalse("Caption and details remain reachable",scroll.canScrollVertically(1));
        });
        getInstrumentation().waitForIdleSync();
    }

    public void testAllQuestionMenusImagesAndZoomStayReadOnly() throws Throwable {
        int before=new kr.example.haliltalk.shop.DemoShopStore(activity.db).balance();
        boolean completed=HelpGuide.isHomeComplete(activity);
        Set<String> seen=new HashSet<>();
        int frames=0;
        for(String context:new String[]{"home","journey","adventure","shop","wardrobe","goals","chat","settings"}){
            HelpGuide guide=open(context);shot(guide,"menu-"+context);
            for(String tutorial:GuideCatalog.menu(context)){
                if(!seen.add(tutorial))continue;
                runTestOnUiThread(()->{
                    View question=guide.getDialog().findViewById(R.id.help_questions).findViewWithTag(tutorial);
                    assertNotNull(question);assertTrue(question.performClick());
                });
                getInstrumentation().waitForIdleSync();
                int[] images=GuideCatalog.get(tutorial).images;
                for(int step=0;step<images.length;step++){
                    assertEquals(step,guide.getDialog().findViewById(R.id.help_progress).getTag());
                    assertEquals(images[step],guide.getDialog().findViewById(R.id.help_image).getTag());
                    shot(guide,tutorial+"-"+(step+1));frames++;
                    if(step<images.length-1)click(guide,R.id.help_next);
                }
                scrollToEnd(guide);
                shot(guide,tutorial+"-end");
                if("action".equals(tutorial)){
                    click(guide,R.id.help_details_toggle);
                    scrollToEnd(guide);shot(guide,"action-details");
                    runTestOnUiThread(()->((android.widget.ScrollView)guide.getDialog()
                            .findViewById(R.id.help_scroll)).fullScroll(View.FOCUS_UP));
                    getInstrumentation().waitForIdleSync();
                    click(guide,R.id.help_image);shot(guide,"action-zoom");
                    click(guide,R.id.help_zoom_in);shot(guide,"action-zoom-in");
                    runTestOnUiThread(()->{
                        GuideImageView viewer=guide.getDialog().findViewById(R.id.help_zoom_image);
                        android.view.accessibility.AccessibilityNodeInfo node=android.view.accessibility.AccessibilityNodeInfo.obtain();
                        viewer.onInitializeAccessibilityNodeInfo(node);
                        boolean moved=false;
                        for(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction action:node.getActionList()){
                            int id=action.getId();
                            if(id==android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.getId()
                                    ||id==android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.getId())
                                moved|=viewer.performAccessibilityAction(id,null);
                        }
                        node.recycle();assertTrue("Enlarged target can be reached without dragging",moved);
                    });
                    getInstrumentation().waitForIdleSync();shot(guide,"action-pan");
                    click(guide,R.id.help_zoom_reset);
                    click(guide,R.id.help_zoom_close);
                }
                click(guide,R.id.help_next);
                assertEquals(View.VISIBLE,guide.getDialog().findViewById(R.id.help_questions).getVisibility());
            }
            runTestOnUiThread(()->{guide.dismiss();activity.getFragmentManager().executePendingTransactions();});
            getInstrumentation().waitForIdleSync();
        }
        assertEquals(12,seen.size());assertEquals(34,frames);
        assertEquals(before,new kr.example.haliltalk.shop.DemoShopStore(activity.db).balance());
        assertEquals(completed,HelpGuide.isHomeComplete(activity));
    }
}
