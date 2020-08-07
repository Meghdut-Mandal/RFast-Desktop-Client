package data

import javax.swing.JComponent
import javax.swing.SwingUtilities

fun onUI(func: () -> Unit) = SwingUtilities.invokeLater {
    func()
}

class JLiveData<T>(private var data: T?) {
    private val list: MutableList<(T) -> Unit> = arrayListOf()
    private var jComponent: JComponent? = null


    fun setValue(newData: T) {
        this.data = newData
        notifyChanges()
    }



    fun observe(jComponent: JComponent, func: (T) -> Unit) = onUI {
        this.jComponent = jComponent
        list.add(func)
        notifyChanges()
    }


    private fun notifyChanges() {
        if (data != null && jComponent != null && jComponent?.isVisible == true) {
            list.forEach {
                println("data>JLiveData>notifyChanges  publishing  ")
                it(data!!)
            }
        }
    }

}